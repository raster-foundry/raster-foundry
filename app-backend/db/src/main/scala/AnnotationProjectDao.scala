package com.rasterfoundry.database

import com.rasterfoundry.common.Config.s3
import com.rasterfoundry.common.S3
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import cats.data._
import cats.effect.{IO, LiftIO}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import geotrellis.vector.{Geometry, Projected}

import java.util.UUID

object AnnotationProjectDao
    extends Dao[AnnotationProject]
    with ObjectPermissions[AnnotationProject]
    with ConnectionIOLogger
    with LazyLogging {
  lazy val s3client = S3()

  val tableName = "annotation_projects"
  override val fieldNames = List(
    "id",
    "created_at",
    "owner",
    "name",
    "project_type",
    "task_size_meters",
    "task_size_pixels",
    "aoi",
    "labelers_team_id",
    "validators_team_id",
    "project_id",
    "status",
    "task_status_summary"
  )

  def selectF: Fragment = fr"SELECT " ++ selectFieldsF ++ fr" FROM " ++ tableF

  def authQuery(
      user: User,
      objectType: ObjectType,
      ownershipTypeO: Option[String],
      groupTypeO: Option[GroupType],
      groupIdO: Option[UUID]
  ): Dao.QueryBuilder[AnnotationProject] =
    user.isSuperuser match {
      case true =>
        Dao.QueryBuilder[AnnotationProject](selectF, tableF, List.empty)
      case false =>
        Dao.QueryBuilder[AnnotationProject](
          selectF,
          tableF,
          List(
            queryObjectsF(
              user,
              objectType,
              ActionType.View,
              ownershipTypeO,
              groupTypeO,
              groupIdO
            )
          )
        )
    }

  def authorized(
      user: User,
      objectType: ObjectType,
      objectId: UUID,
      actionType: ActionType
  ): ConnectionIO[AuthResult[AnnotationProject]] =
    this.query
      .filter(authorizedF(user, objectType, actionType))
      .filter(objectId)
      .selectOption
      .map(AuthResult.fromOption _)

  def getProjectById(
      annotationProjectId: UUID
  ): ConnectionIO[Option[AnnotationProject]] =
    this.query.filter(annotationProjectId).selectOption

  def listProjects(
      page: PageRequest,
      params: AnnotationProjectQueryParameters,
      user: User
  ): ConnectionIO[PaginatedResponse[AnnotationProject.WithRelated]] =
    authQuery(
      user,
      ObjectType.AnnotationProject,
      params.ownershipTypeParams.ownershipType,
      params.groupQueryParameters.groupType,
      params.groupQueryParameters.groupId
    ).filter(params)
      .page(page)
      .flatMap(toWithRelated)

  def toWithRelated(
      projectsPage: PaginatedResponse[AnnotationProject]
  ): ConnectionIO[PaginatedResponse[AnnotationProject.WithRelated]] =
    projectsPage.results.toList.toNel match {
      case Some(projects) =>
        projects traverse { project =>
          for {
            tileLayers <- TileLayerDao.listByProjectId(project.id)
            labelClassGroups <- AnnotationLabelClassGroupDao.listByProjectId(
              project.id
            )
            labelClassGroupsWithClasses <- labelClassGroups traverse {
              labelClassGroup =>
                AnnotationLabelClassDao.listAnnotationLabelClassByGroupId(
                  labelClassGroup.id
                ) map { cls =>
                  labelClassGroup.withLabelClasses(cls)
                }
            }
          } yield {
            project.withRelated(tileLayers, labelClassGroupsWithClasses)
          }
        } map { projectWithRelated =>
          PaginatedResponse[AnnotationProject.WithRelated](
            projectsPage.count,
            projectsPage.hasPrevious,
            projectsPage.hasNext,
            projectsPage.page,
            projectsPage.pageSize,
            projectWithRelated.toList
          )

        }
      case _ =>
        projectsPage
          .copy(results = List.empty[AnnotationProject.WithRelated])
          .pure[ConnectionIO]
    }

  def insert(
      newAnnotationProject: AnnotationProject.Create,
      user: User
  ): ConnectionIO[AnnotationProject.WithRelated] = {
    val projectInsert =
      (fr"INSERT INTO" ++ tableF ++ fr"(" ++ insertFieldsF ++ fr")" ++
        fr"""VALUES
      (uuid_generate_v4(), now(), ${user.id}, ${newAnnotationProject.name},
       ${newAnnotationProject.projectType}, DEFAULT, ${newAnnotationProject.taskSizePixels},
       ${newAnnotationProject.aoi}, ${newAnnotationProject.labelersTeamId},
       ${newAnnotationProject.validatorsTeamId},
       ${newAnnotationProject.projectId}, ${newAnnotationProject.status},
       '{"UNLABELED": 0, "LABELING_IN_PROGRESS": 0, "LABELED": 0, "VALIDATION_IN_PROGRESS": 0, "VALIDATED": 0 }'::jsonb
       )
    """).update.withUniqueGeneratedKeys[AnnotationProject](
        fieldNames: _*
      )

    for {
      annotationProject <- projectInsert
      tileLayers <- newAnnotationProject.tileLayers traverse { layer =>
        TileLayerDao.insertTileLayer(layer, annotationProject)
      }
      labelClassGroups <- newAnnotationProject.labelClassGroups.zipWithIndex traverse {
        case (classGroup, idx) =>
          AnnotationLabelClassGroupDao.insertAnnotationLabelClassGroup(
            classGroup,
            annotationProject,
            idx
          )
      }
    } yield annotationProject.withRelated(tileLayers, labelClassGroups)
  }

  def getById(id: UUID): ConnectionIO[Option[AnnotationProject]] =
    query.filter(id).selectOption

  def unsafeGetById(id: UUID): ConnectionIO[AnnotationProject] =
    query.filter(id).select

  def getWithRelatedById(
      id: UUID
  ): ConnectionIO[Option[AnnotationProject.WithRelated]] =
    for {
      projectO <- getById(id)
      tileLayers <- TileLayerDao.listByProjectId(id)
      labelClassGroup <- AnnotationLabelClassGroupDao
        .listByProjectId(id)
      labelClassGroupWithClass <- labelClassGroup traverse { group =>
        AnnotationLabelClassDao
          .listAnnotationLabelClassByGroupId(group.id)
          .map(group.withLabelClasses(_))
      }
    } yield {
      projectO map { project =>
        project.withRelated(tileLayers, labelClassGroupWithClass)
      }
    }

  def getWithRelatedAndSummaryById(
      id: UUID
  ): ConnectionIO[Option[AnnotationProject.WithRelatedAndLabelClassSummary]] =
    for {
      withRelatedO <- getWithRelatedById(id)
      labelClassGroups <- AnnotationLabelClassGroupDao.listByProjectId(id)
      labelClassSummaries <- labelClassGroups traverse { labelClassGroup =>
        AnnotationLabelDao.countByProjectAndGroup(id, labelClassGroup.id).map {
          summary =>
            AnnotationProject.LabelClassGroupSummary(
              labelClassGroup.id,
              labelClassGroup.name,
              summary
            )
        }
      }
    } yield {
      withRelatedO map { withRelated =>
        withRelated.withSummary(
          labelClassSummaries
        )
      }
    }

  def deleteById(id: UUID, user: User): ConnectionIO[Int] =
    for {
      annotationProject <- query.filter(id).selectOption
      _ <- debug(s"Got annotation project ${annotationProject map { _.id }}")
      sourceProject <- annotationProject flatMap { _.projectId } traverse {
        projectId =>
          ProjectDao.unsafeGetProjectById(projectId)
      }
      uploads <- UploadDao.findForAnnotationProject(id)
      userUploads = uploads.filter(_.owner == user.id)
      uploadFiles = userUploads flatMap { _.files } flatMap { (f: String) =>
        val (bucket, key) = uriToBucketAndKey(f.replace("|", "%7C"))
        if (bucket == s3.dataBucket && key.contains(user.id)) {
          Some((bucket, key))
        } else {
          None
        }
      }
      _ <- debug(s"Planning to delete ${uploadFiles.size} files from S3")
      _ <- uploadFiles traverse {
        case (bucket, key) =>
          LiftIO[ConnectionIO].liftIO {
            (IO { s3client.deleteObject(bucket, key) }).attempt
          }
      }
      _ <- debug(s"Deleting uploads ${userUploads map { _.id }}")
      _ <- userUploads traverse { upload =>
        UploadDao.query.filter(upload.id).delete
      }
      _ <- debug(s"Source project is: ${sourceProject map { _.id }}")
      projectScenes <- sourceProject map { _.defaultLayerId } traverse {
        projectLayerId =>
          ProjectLayerScenesDao.listLayerScenesRaw(projectLayerId, None)
      }
      _ <- debug(s"Project scenes are: ${projectScenes map { _ map { _.id } }}")
      _ <- projectScenes traverse { scenes =>
        scenes traverse {
          case scene if (scene.bucketAndKey map { bk: (String, String) =>
                bk._1 == s3.dataBucket && bk._2.contains(user.id)
              }).getOrElse(false) =>
            val Some((bucket, key)) = scene.bucketAndKey
            debug(s"Deleting ${scene.id} and its data") *>
              (LiftIO[ConnectionIO].liftIO {
                IO { s3client.deleteObject(bucket, key) }
              }).attempt *> SceneDao.query
              .filter(scene.id)
              .filter(fr"owner = ${user.id}")
              .delete
          case scene =>
            debug(s"Deleting scene: ${scene.id}") *>
              SceneDao.query
                .filter(scene.id)
                .filter(fr"owner = ${user.id}")
                .delete
        }
      }
      _ <- sourceProject.filter(_.owner == user.id) traverse { project =>
        debug(s"Deleting project ${project.id}") *> ProjectDao.deleteProject(
          project.id
        )
      }
      n <- query.filter(fr"id = ${id}").delete
    } yield n

  def update(project: AnnotationProject, id: UUID): ConnectionIO[Int] = {
    (fr"UPDATE " ++ tableF ++ fr"""SET
      name = ${project.name},
      labelers_team_id = ${project.labelersTeamId},
      validators_team_id = ${project.validatorsTeamId},
      task_size_meters= ${project.taskSizeMeters},
      aoi = ${project.aoi},
      status = ${project.status}
    WHERE
      id = $id
    """).update.run;
  }

  def getFootprint(id: UUID): ConnectionIO[Option[Projected[Geometry]]] =
    for {
      annotationProjectO <- getById(id)
      footprint <- annotationProjectO match {
        case Some(annotationProject) =>
          annotationProject.aoi match {
            case Some(aoi) => Some(aoi).pure[ConnectionIO]
            case _ =>
              annotationProject.projectId
                .traverse(projectId => ProjectDao.getFootprint(projectId))
                .map(_.flatten)
          }
        case _ => None.pure[ConnectionIO]
      }
    } yield {
      footprint
    }

  def countUserProjects(user: User): ConnectionIO[Long] =
    query.filter(user).count

  def getShareCount(id: UUID, userId: String): ConnectionIO[Long] =
    getPermissions(id)
      .map { acrList =>
        acrList
          .foldLeft(Set.empty[String])(
            (accum: Set[String], acr: ObjectAccessControlRule) => {
              acr match {
                case ObjectAccessControlRule(
                    SubjectType.User,
                    Some(subjectId),
                    _
                    ) if subjectId != userId =>
                  Set(subjectId) | accum
                case _ => accum
              }
            }
          )
          .size
          .toLong
      }

  def getAllShareCounts(userId: String): ConnectionIO[Map[UUID, Long]] =
    for {
      projectIds <- (fr"select id from " ++ Fragment.const(tableName) ++ fr" where owner = $userId")
        .query[UUID]
        .to[List]
      projectShareCounts <- projectIds traverse { id =>
        getShareCount(id, userId).map((id -> _))
      }
    } yield projectShareCounts.toMap

  def getAnnotationProjectStacInfo(
      annotationProjectId: UUID
  ): ConnectionIO[Option[StacLabelItemPropertiesThin]] =
    (for {
      annotationProject <- OptionT {
        getProjectById(annotationProjectId)
      }
      labelGroups <- OptionT.liftF(
        AnnotationLabelClassGroupDao.listByProjectId(annotationProjectId)
      )
      groupedLabelClasses <- OptionT.liftF(labelGroups traverse { group =>
        AnnotationLabelClassDao
          .listAnnotationLabelClassByGroupId(group.id)
          .map((group.id, _))
      })
      groupToLabelClasses = groupedLabelClasses.map(g => g._1 -> g._2).toMap
      stacInfo = StacLabelItemPropertiesThin(
        labelGroups.map(_.name),
        labelGroups.map { group =>
          groupToLabelClasses
            .get(group.id)
            .map(
              classes =>
                StacLabelItemProperties.StacLabelItemClasses(
                  group.name,
                  classes.map(_.name)
              )
            )
        }.flatten,
        "vector",
        annotationProject.projectType.toString.toLowerCase
      )
    } yield stacInfo).value

  def getSharedUsers(projectId: UUID): ConnectionIO[List[UserThin]] = {
    for {
      permissions <- getPermissions(projectId)
      idsNel = permissions
        .filter(
          _.subjectType == SubjectType.User
        )
        .flatMap(_.subjectId)
        .toNel
      users <- idsNel match {
        case Some(ids) => UserDao.getThinUsersForIds(ids)
        case _         => List.empty.pure[ConnectionIO]
      }
    } yield users
  }

  def deleteSharedUser(projectId: UUID, userId: String): ConnectionIO[Int] = {
    for {
      permissions <- getPermissions(projectId)
      permissionsToKeep = permissions collect {
        case p if p.subjectId != Some(userId) => p
      }
      numberDeleted <- permissionsToKeep match {
        case Nil => deletePermissions(projectId)
        case ps if ps.toSet != permissions.toSet =>
          replacePermissions(projectId, ps) map { _ =>
            permissions.size - ps.size
          }
        case _ =>
          0.pure[ConnectionIO]
      }
    } yield numberDeleted
  }

  def copyProject(
      projectId: UUID,
      user: User
  ): ConnectionIO[AnnotationProject] = {
    val insertQuery = (fr"""
           INSERT INTO""" ++ tableF ++ fr"(" ++ insertFieldsF ++ fr")" ++
      fr"""SELECT
             uuid_generate_v4(), now(), ${user.id}, name, project_type, task_size_meters, task_size_pixels,
             aoi, labelers_team_id, validators_team_id, project_id, status, task_status_summary
           FROM """ ++ tableF ++ fr"""
           WHERE id = ${projectId}
        """)
    for {
      annotationProjectCopy <- insertQuery.update
        .withUniqueGeneratedKeys[AnnotationProject](
          fieldNames: _*
        )
      classGroups <- AnnotationLabelClassGroupDao.listByProjectId(projectId)
      _ <- classGroups traverse { classGroup =>
        for {
          labelClasses <- AnnotationLabelClassDao
            .listAnnotationLabelClassByGroupId(classGroup.id)
          newClassGroup <- AnnotationLabelClassGroupDao
            .insertAnnotationLabelClassGroup(
              AnnotationLabelClassGroup.Create(
                classGroup.name,
                Some(classGroup.index),
                labelClasses.map { labelClass =>
                  AnnotationLabelClass.Create(
                    labelClass.name,
                    labelClass.colorHexCode,
                    labelClass.default,
                    labelClass.determinant,
                    labelClass.index
                  )
                }
              ),
              annotationProjectCopy,
              0
            )
        } yield newClassGroup
      }
      _ <- TaskDao.copyAnnotationProjectTasks(
        projectId,
        annotationProjectCopy.id,
        user
      )
      _ <- TileLayerDao.copyTileLayersForProject(
        projectId,
        annotationProjectCopy.id
      )
    } yield annotationProjectCopy
  }
}
