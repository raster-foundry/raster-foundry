package com.azavea.rf.database

import java.util.UUID

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel.{BatchParams, ColorCorrect, MosaicDefinition, SceneToProject}
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import geotrellis.slick.Projected
import geotrellis.vector.Polygon

import geotrellis.raster.histogram._
import doobie.Fragments._

import doobie.Fragments._
import scala.concurrent.Future


object SceneToProjectDao extends Dao[SceneToProject] {

  val tableName = "scenes_to_projects"

  val selectF = sql"""
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

  def setManualOrder(projectId: UUID, sceneIds: Seq[UUID]): ConnectionIO[Seq[UUID]] = {
    val updates = for {
      i <- sceneIds.indices
    } yield {
      fr"""
      UPDATE scenes_to_projects SET scene_order = ${i} WHERE project_id = ${projectId} AND scene_id = ${sceneIds(i)}
    """.update.run
    }
    for {
      _ <- updates.toList.sequence
    } yield sceneIds
  }

  // Check swagger spec for appropriate return type
  def getMosaicDefinition(projectId: UUID, polygonOption: Option[Projected[Polygon]]): ConnectionIO[Seq[MosaicDefinition]] = {

    val filters = List(
      polygonOption.map(polygon => fr"ST_Intersects(scenes.tile_footprint, ${polygon}"),
      Some(fr"scenes_to_projects.project_id = ${projectId}")
    )
    val select = fr"""
    SELECT
      scene_id, project_id, scene_order, mosaic_definition, accepted
    FROM
      scenes_to_projects
    LEFT JOIN
      scenes
    ON scenes.id = scenes_to_projects.scene_id
      """
    for {
      stps <- (select ++ whereAndOpt(filters: _*)).query[SceneToProject].list
    } yield {
      MosaicDefinition.fromScenesToProjects(stps)
    }
  }

  def getMosaicDefinition(projectId: UUID): ConnectionIO[Seq[MosaicDefinition]] = {
    getMosaicDefinition(projectId, None)
  }

  def setColorCorrectParams(projectId: UUID, sceneId: UUID, colorCorrectParams: ColorCorrect.Params): ConnectionIO[SceneToProject] = {
    fr"""
      UPDATE scenes_to_projects SET mosaic_definition = ${colorCorrectParams} WHERE project_id = ${projectId} AND scene_id = ${sceneId}
    """.update.withUniqueGeneratedKeys("scene_id", "project_id", "accepted", "scene_order", "mosaic_definition")
  }

  def getColorCorrectParams(projectId: UUID, sceneId: UUID): ConnectionIO[ColorCorrect.Params] = {
    query
      .filter(fr"project_id = ${projectId} AND scene_id = ${sceneId}")
      .select
      .map { stp: SceneToProject => stp.colorCorrectParams }
  }

  def setColorCorrectParamsBatch(projectId: UUID, batchParams: BatchParams): ConnectionIO[List[SceneToProject]] = {
    val updates: ConnectionIO[List[SceneToProject]] = batchParams.items.map( params => setColorCorrectParams(projectId, params.sceneId, params.params)).sequence
    updates
  }

  def getSceneHistogram(projectId: UUID): ConnectionIO[Option[List[Array[Histogram[Int]]]]] = ???
}

