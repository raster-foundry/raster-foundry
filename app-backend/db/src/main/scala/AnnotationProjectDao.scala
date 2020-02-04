package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

object AnnotationProjectDao extends Dao[AnnotationProject] {
  val tableName = "annotation_projects"

  def selectF: Fragment = sql"""
    SELECT
      id, created_at, owner, name, project_type, task_size_meters,
      aoi, labelers_team_id, validators_team_id, project_id
    FROM
  """ ++ tableF

  // TODO: insert tile layers
  // TODO: insert annotation label class groups
  def insertAnnotationProject(
      newAnnotationProject: AnnotationProject.Create,
      user: User
  ): ConnectionIO[AnnotationProject] = {
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
      _ <- newAnnotationProject.tileLayers traverse { layer =>
        TileLayerDao.insertTileLayer(layer, annotationProject)
      }
    } yield annotationProject
  }
}
