package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

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
  ): Dao.QueryBuilder[AnnotationProject] = ???

  def authorized(
      user: User,
      objectType: ObjectType,
      objectId: UUID,
      actionType: ActionType
  ): ConnectionIO[AuthResult[AnnotationProject]] = ???

  def insertAnnotationProject(
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

  def countUserProjects(user: User): ConnectionIO[Long] =
    query.filter(user).count
}
