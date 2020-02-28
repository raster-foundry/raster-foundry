package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import cats.data._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import geotrellis.vector.{Geometry, Projected}

import java.util.UUID

object AnnotationProjectDao
    extends Dao[AnnotationProject]
    with ObjectPermissions[AnnotationProject] {
  val tableName = "annotation_projects"

  def selectF: Fragment = sql"""
    SELECT
      id, created_at, owner, name, project_type, task_size_meters,
      task_size_pixels, aoi, labelers_team_id, validators_team_id,
      project_id, ready
    FROM
  """ ++ tableF

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
    val projectInsert = (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, created_at, owner, name, project_type, task_size_pixels,
       aoi, labelers_team_id, validators_team_id, project_id, ready)
    VALUES
      (uuid_generate_v4(), now(), ${user.id}, ${newAnnotationProject.name},
       ${newAnnotationProject.projectType}, ${newAnnotationProject.taskSizePixels},
       ${newAnnotationProject.aoi}, ${newAnnotationProject.labelersTeamId},
       ${newAnnotationProject.validatorsTeamId},
       ${newAnnotationProject.projectId}, ${newAnnotationProject.ready})
    """).update.withUniqueGeneratedKeys[AnnotationProject](
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
      "ready"
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

  def annotationProjectByIdQuery(id: UUID): Query0[AnnotationProject] = {
    (selectF ++ Fragments.whereAndOpt(Some(fr"id = ${id}")))
      .query[AnnotationProject]
  }

  def getById(id: UUID): ConnectionIO[Option[AnnotationProject]] =
    annotationProjectByIdQuery(id).option

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
  ): ConnectionIO[Option[AnnotationProject.WithRelatedAndSummary]] =
    for {
      withRelatedO <- getWithRelatedById(id)
      taskStatusSummary <- TaskDao.countProjectTaskByStatus(id)
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
          taskStatusSummary,
          labelClassSummaries
        )
      }
    }

  def deleteById(id: UUID): ConnectionIO[Int] =
    query.filter(fr"id = ${id}").delete

  def update(project: AnnotationProject, id: UUID): ConnectionIO[Int] = {
    (fr"UPDATE " ++ tableF ++ fr"""SET
      name = ${project.name},
      labelers_team_id = ${project.labelersTeamId},
      validators_team_id = ${project.validatorsTeamId},
      task_size_meters= ${project.taskSizeMeters},
      aoi = ${project.aoi},
      ready = ${project.ready}
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
      permissions <- AnnotationProjectDao.getPermissions(projectId)
      idsNel = permissions
        .filter(
          _.subjectType == SubjectType.User
        )
        .flatMap(_.subjectId)
        .toNel
      users <- idsNel match {
        case Some(ids) => UserDao.getThinUsersForIds(ids)
        case _ => List.empty.pure[ConnectionIO]
      }
    } yield users
  }

  def deleteSharedUser(projectId: UUID, userId: String): ConnectionIO[Int] = {
    for {
      permissions <- AnnotationProjectDao.getPermissions(projectId)
      permissionsToKeep = permissions.filter(
        p => p.subjectId.map(id => id != userId).getOrElse(true)
      )
      permissionsResult <- permissions.size > permissionsToKeep.size && permissions.size > 0 match {
        case true => replacePermissions(projectId, permissionsToKeep)
        case _    => (permissions).pure[ConnectionIO]
      }
    } yield (permissions.size - permissionsResult.size)
  }
}
