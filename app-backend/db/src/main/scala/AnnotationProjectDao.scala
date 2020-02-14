package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

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
      aoi, labelers_team_id, validators_team_id, project_id
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
      (id, created_at, owner, name, project_type, task_size_meters,
       aoi, labelers_team_id, validators_team_id, project_id)
    VALUES
      (uuid_generate_v4(), now(), ${user.id}, ${newAnnotationProject.name},
       ${newAnnotationProject.projectType}, ${newAnnotationProject.taskSizeMeters},
       ${newAnnotationProject.aoi}, ${newAnnotationProject.labelersTeamId},
       ${newAnnotationProject.validatorsTeamId},
       ${newAnnotationProject.projectId})
    """).update.withUniqueGeneratedKeys[AnnotationProject](
      "id",
      "created_at",
      "owner",
      "name",
      "project_type",
      "task_size_meters",
      "aoi",
      "labelers_team_id",
      "validators_team_id",
      "project_id"
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
      validators_team_id = ${project.validatorsTeamId}
    WHERE
      id = $id
    """).update.run;
  }

  def updateWithRelated(
      id: UUID,
      projectWithRelated: AnnotationProject.WithRelated
  ): ConnectionIO[Int] = {
    val project = projectWithRelated.toProject
    for {
      count <- update(project, id)
      _ <- TileLayerDao.deleteByProjectId(id)
      _ <- projectWithRelated.tileLayers traverse { layer =>
        TileLayerDao.insertTileLayer(
          TileLayer.Create(
            layer.name,
            layer.url,
            Some(layer.default),
            Some(layer.overlay),
            layer.layerType
          ),
          project
        )
      }
      _ <- AnnotationLabelClassGroupDao.deleteByProjectId(id)
      _ <- projectWithRelated.labelClassGroups.zipWithIndex traverse {
        case (classGroup, idx) =>
          AnnotationLabelClassGroupDao.insertAnnotationLabelClassGroup(
            AnnotationLabelClassGroup.Create(
              classGroup.id.toString(),
              Some(classGroup.index),
              classGroup.labelClasses map { labelClass =>
                AnnotationLabelClass.Create(
                  labelClass.name,
                  labelClass.colorHexCode,
                  labelClass.default,
                  labelClass.determinant,
                  labelClass.index
                )
              }
            ),
            project,
            idx
          )
      }
    } yield { count }
  }

  def getFootprint(id: UUID): ConnectionIO[Option[Projected[Geometry]]] =
    for {
      annotationProjectO <- getById(id)
      result <- annotationProjectO match {
        case Some(annotationProject) =>
          annotationProject.aoi match {
            case Some(aoi) => Some(aoi).pure[ConnectionIO]
            case _ =>
              annotationProject.projectId match {
                case Some(projectId) => ProjectDao.getFootprint(projectId)
                case _               => None.pure[ConnectionIO]
              }
          }
        case _ => None.pure[ConnectionIO]
      }
    } yield {
      result
    }

  def countUserProjects(user: User): ConnectionIO[Long] =
    query.filter(user).count

  def getShareCount(id: UUID, userId: String): ConnectionIO[Long] =
    getPermissions(id)
      .map { acrList =>
        acrList.collect {
          case ObjectAccessControlRule(subjType, Some(subjectId), _)
              if subjType == SubjectType.User && subjectId != userId =>
            subjectId
        }
      }
      .map(_.distinct.length.toLong)
}
