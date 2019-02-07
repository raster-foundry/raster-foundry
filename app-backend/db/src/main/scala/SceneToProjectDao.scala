package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.common.datamodel._

import cats.data.{NonEmptyList => NEL}
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import fs2.Stream
import geotrellis.vector.{Polygon, Projected}
import com.typesafe.scalalogging.LazyLogging

import java.util.UUID

@SuppressWarnings(Array("EmptyCaseClass"))
final case class SceneToProjectDao()

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
    for {
      // Streamed only for correct monadic context
      layerId <- ProjectDao.projectByIdQuery(projectId).stream map { project =>
        project.defaultLayerId
      }
      scenes <- SceneToLayerDao.getMosaicDefinition(layerId,
                                                    polygonOption,
                                                    redBand,
                                                    greenBand,
                                                    blueBand,
                                                    sceneIdSubset)
    } yield scenes
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
    SET mosaic_definition =
      (mosaic_definition ||
        json_build_object(
          'redBand',${colorBands.redBand},
          'blueBand',${colorBands.blueBand},
          'greenBand', ${colorBands.greenBand}
        )::jsonb
      )
    WHERE project_id = ${projectId}
    """).update.run
  }
}
