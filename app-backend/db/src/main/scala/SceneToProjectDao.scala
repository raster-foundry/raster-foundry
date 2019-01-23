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

case class SceneToProjectDao()

object SceneToProjectDao extends Dao[SceneToProject] with LazyLogging {

  val tableName = "scenes_to_projects"

  val selectF: Fragment = sql"""
    SELECT
      scene_id, project_id, accepted, scene_order, mosaic_definition
    FROM
  """ ++ tableF

  /** Unclear what this is supposed to return from the current implementation */
  def acceptScene(projectId: UUID, sceneId: UUID): ConnectionIO[Int] = {
    fr"""
      UPDATE scenes_to_projects SET accepted = true WHERE project_id = ${projectId} AND scene_id = ${sceneId}
    """.update.run
  }

  def acceptScenes(projectId: UUID, sceneIds: List[UUID]): ConnectionIO[Int] = {
    sceneIds.toNel match {
      case Some(ids) => acceptScenes(projectId, ids)
      case _         => 0.pure[ConnectionIO]
    }
  }

  def acceptScenes(projectId: UUID, sceneIds: NEL[UUID]): ConnectionIO[Int] = {
    val updateF: Fragment = fr"""
      UPDATE scenes_to_projects
      SET accepted = true
    """ ++ Fragments.whereAnd(
      fr"project_id = $projectId",
      Fragments.in(fr"scene_id", sceneIds)
    )
    updateF.update.run
  }

  def addSceneOrdering(projectId: UUID): ConnectionIO[Int] = {
    val updateF = fr"""
    UPDATE scenes_to_projects
    SET scene_order = rnum
    (
    SELECT id, row_number() over (ORDER BY scene_order ASC, acquisition_date ASC, cloud_cover ASC) as rnum,
    FROM scenes_to_projects join scenes on scenes.id = scene_id where project_id = $projectId
    ) s
    WHERE id = s.id
    """
    updateF.update.run
  }

  def moveSceneOrder(projectId: UUID, from: Int, to: Int): ConnectionIO[Int] = {
    // TODO implement this. Route is currently commented out
    // val updateF = fr"""
    // """
    // updateF.update.run
    ???
  }

  def setManualOrder(projectId: UUID,
                     sceneIds: Seq[UUID]): ConnectionIO[Seq[UUID]] = {
    val updates = for {
      i <- sceneIds.indices
    } yield {
      fr"""
      UPDATE scenes_to_projects SET scene_order = ${i} WHERE project_id = ${projectId} AND scene_id = ${sceneIds(
        i)}
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

    val orderByF: Fragment = fr"""
      ORDER BY scenes_stp.scene_order ASC NULLS LAST, (acquisition_date, cloud_cover) ASC
    """

    val select = fr"""
    SELECT
      scene_id, project_id, accepted, scene_order, mosaic_definition, scene_type, ingest_location,
      data_footprint, is_single_band, single_band_options
    FROM (
      scenes_to_projects
    LEFT JOIN
      scenes
    ON scenes.id = scenes_to_projects.scene_id
    ) scenes_stp
    LEFT JOIN
      projects
    ON
      scenes_stp.project_id = projects.id
      """
    (select ++ whereAndOpt(filters: _*) ++ orderByF)
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

  def getMosaicDefinition(
      projectId: UUID): Stream[ConnectionIO, MosaicDefinition] = {
    getMosaicDefinition(projectId, None)
  }

  def setColorCorrectParams(
      projectId: UUID,
      sceneId: UUID,
      colorCorrectParams: ColorCorrect.Params): ConnectionIO[SceneToProject] = {
    fr"""
      UPDATE scenes_to_projects SET mosaic_definition = ${colorCorrectParams} WHERE project_id = ${projectId} AND scene_id = ${sceneId}
    """.update.withUniqueGeneratedKeys("scene_id",
                                       "project_id",
                                       "accepted",
                                       "scene_order",
                                       "mosaic_definition")
  }

  def getColorCorrectParams(
      projectId: UUID,
      sceneId: UUID): ConnectionIO[ColorCorrect.Params] = {
    query
      .filter(fr"project_id = ${projectId} AND scene_id = ${sceneId}")
      .select
      .map { stp: SceneToProject =>
        stp.colorCorrectParams
      }
  }

  def setColorCorrectParamsBatch(
      projectId: UUID,
      batchParams: BatchParams): ConnectionIO[List[SceneToProject]] = {
    val updates: ConnectionIO[List[SceneToProject]] = batchParams.items
      .map(params =>
        setColorCorrectParams(projectId, params.sceneId, params.params))
      .sequence
    updates
  }

  def setProjectColorBands(
      projectId: UUID,
      colorBands: ProjectColorModeParams): ConnectionIO[Int] = {
    // TODO support setting color band by datasource instead of project wide
    // if there is not a mosaic definition at this point, then the scene_to_project row was not created correctly
    (fr"""
    UPDATE scenes_to_projects
    SET mosaic_definition = (mosaic_definition || '{"redBand":""" ++ Fragment
      .const(s"${colorBands.redBand}") ++
      fr""", "blueBand":""" ++ Fragment.const(s"${colorBands.blueBand}") ++
      fr""", "greenBand":""" ++ Fragment.const(s"${colorBands.greenBand}") ++
      fr"""}'::jsonb)
    WHERE project_id = $projectId
    """).update.run
  }
}
