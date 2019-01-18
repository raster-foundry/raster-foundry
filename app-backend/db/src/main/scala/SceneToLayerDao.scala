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
      UPDATE scenes_to_layers
      SET accepted = true
      WHERE project_layer_id = ${projectLayerId}
        AND scene_id = ${sceneId}
    """.update.run
  }

  def acceptScenes(projectLayerId: UUID, sceneIds: List[UUID]): ConnectionIO[Int] = {
    sceneIds.toNel match {
      case Some(ids) => acceptScenes(projectLayerId, ids)
      case _         => 0.pure[ConnectionIO]
    }
  }

  def addSceneOrdering(projectId: UUID): ConnectionIO[Int] = {
    (fr"""
      UPDATE scenes_to_layers
      SET scene_order = s.rnum
      FROM (
        SELECT scene_id, project_layer_id, row_number() over (ORDER BY stl.scene_order ASC, scenes.acquisition_date ASC, scenes.cloud_cover ASC) as rnum
        FROM scenes_to_layers stl
          JOIN scenes ON scenes.id = stl.scene_id
          JOIN project_layers pl ON pl.id = stl.project_layer_id
          WHERE pl.project_id = ${projectId}
      ) s
      WHERE scenes_to_layers.project_layer_id = s.project_layer_id
        AND scenes_to_layers.scene_id = s.scene_id
    """).update.run
  }

  def setManualOrder(projectLayerId: UUID,
                     sceneIds: Seq[UUID],): ConnectionIO[Seq[UUID]] = {
    val updates = for {
      i <- sceneIds.indices
    } yield {
      fr"""
      UPDATE scenes_to_layers
      SET scene_order = ${i}
      WHERE project_layer_id = ${projectLayerId}
        AND scene_id = ${sceneIds(i)}
    """.update.run
    }
    for {
      _ <- updates.toList.sequence
    } yield sceneIds
  }

  def getMosaicDefinition(projectId: UUID,
                          polygonOption: Option[Projected[Polygon]],
                          redBand: Option[Int] = None,
                          greenBand: Option[Int] = None,
                          blueBand: Option[Int] = None,
                          sceneIdSubset: List[UUID] = List.empty)
    : Stream[ConnectionIO, MosaicDefinition] = {
    val filters = List(
      polygonOption.map(polygon =>
        fr"ST_Intersects(scenes_stp.tile_footprint, ${polygon})"),
      Some(fr"scenes_stp.project_id = ${projectId}"),
      Some(fr"scenes_stp.accepted = true"),
      Some(fr"scenes_stp.ingest_status = 'INGESTED'"),
      sceneIdSubset.toNel map {
        Fragments.in(fr"scene_id", _)
      }
    )
    val select = fr"""
    SELECT
      scene_id, project_layer_id, accepted, scene_order, mosaic_definition, scene_type, ingest_location,
      data_footprint, is_single_band, single_band_options
    FROM (
      scenes_to_layers
    LEFT JOIN
      scenes
    ON scenes.id = scenes_to_projects.scene_id
    ) scenes_stp
    LEFT JOIN
      project_layers
    ON
      scenes_stp.project_layer_id = project_layers.id
    LEFT JOIN
      projects
    ON
      scenes_stp.project_id = projects.id
      """
    (select ++ whereAndOpt(filters: _*) ++ fr"ORDER BY scenes_stp.scene_order ASC")
      .query[SceneToProjectwithSceneType]
      .stream map { stp =>
      {
        Applicative[Option].map3(redBand, greenBand, blueBand) {
          case (r, g, b) =>
            MosaicDefinition(
              stp.sceneId,
              stp.colorCorrectParams.copy(
                redBand = r,
                greenBand = g,
                blueBand = b
              ),
              stp.sceneType,
              stp.ingestLocation,
              stp.dataFootprint flatMap { _.geom.as[MultiPolygon] },
              stp.isSingleBand,
              stp.singleBandOptions
            )
        } getOrElse {
          MosaicDefinition(
            stp.sceneId,
            stp.colorCorrectParams,
            stp.sceneType,
            stp.ingestLocation,
            stp.dataFootprint flatMap { _.geom.as[MultiPolygon] },
            stp.isSingleBand,
            stp.singleBandOptions
          )
        }
      }
    }
  }
}
