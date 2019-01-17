package com.rasterfoundry.database

import java.util.UUID

import cats.Applicative
import cats.data.{NonEmptyList => NEL, _}
import cats.implicits._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.datamodel.{
  BatchParams,
  ColorCorrect => RFColorCorrect,
  MosaicDefinition,
  SceneToProject,
  SceneToProjectwithSceneType
}
import com.typesafe.scalalogging.LazyLogging
import com.rasterfoundry.datamodel._
import doobie._
import doobie.Fragments._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import fs2.Stream
import geotrellis.vector.{MultiPolygon, Polygon, Projected}
import io.circe.syntax._

case class SceneToLayerDao()

object SceneToLayerDao extends Dao[SceneToLayer] with LazyLogging {

  val tableName = "scenes_to_layers"

  val selectF: Fragment = sql"""
    SELECT
      scene_id, project_layer_id, accepted, scene_order, mosaic_definition
    FROM
  """ ++ tableF

  def acceptScene(projectLayerId: UUID, sceneId: UUID): ConnectionIO[Int] = {
    fr"""
      UPDATE scenes_to_layers SET accepted = true WHERE project_layer_id = ${projectLayerId} AND scene_id = ${sceneId}
    """.update.run
  }

  def acceptScenes(projectLayerId: UUID, sceneIds: List[UUID]): ConnectionIO[Int] = {
    sceneIds.toNel match {
      case Some(ids) => acceptScenes(projectLayerId, ids)
      case _         => 0.pure[ConnectionIO]
    }
  }

  def addSceneOrdering(projectLayerId: UUID): ConnectionIO[Int] = {
    (fr"""
    UPDATE scenes_to_layers
    SET scene_order = rnum
    (
    SELECT id, row_number() over (ORDER BY scene_order ASC, acquisition_date ASC, cloud_cover ASC) as rnum,
    FROM scenes_to_layers join scenes on scenes.id = scene_id where project_layer_id = $projectLayerId
    ) s
    WHERE id = s.id
    """).update.run
  }
}
