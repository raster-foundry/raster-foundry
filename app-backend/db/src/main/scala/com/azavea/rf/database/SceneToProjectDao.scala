package com.azavea.rf.database

import java.util.UUID

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel.{BatchParams, ColorCorrect, MosaicDefinition, SceneToProject, SceneToProjectwithSceneType}
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.slick.Projected
import geotrellis.vector.{MultiPolygon, Polygon}
import geotrellis.raster.histogram._
import doobie.Fragments._
import doobie.Fragments._

import scala.concurrent.Future


object SceneToProjectDao extends Dao[SceneToProject] with LazyLogging {

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

  def acceptScenes(projectId: UUID, sceneIds: List[UUID]): ConnectionIO[Int] = {
    sceneIds.toNel match {
      case Some(ids) => acceptScenes(projectId, ids)
      case _ => 0.pure[ConnectionIO]
    }
  }

  def acceptScenes(projectId: UUID, sceneIds: NonEmptyList[UUID]): ConnectionIO[Int] = {
    val updateF: Fragment =fr"""
      UPDATE scenes_to_projects
      SET accepted = true
    """ ++ Fragments.whereAnd(
      fr"project_id = $projectId",
      Fragments.in(fr"scene_id", sceneIds)
    )
    updateF.update.run
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
  // we filter to make sure the list only includes non-None geometries, and then perform unions of
  // multipolygons that we know are stored in the same projection in the database
  @SuppressWarnings(Array("OptionGet"))
  def getMosaicDefinition(projectId: UUID, polygonOption: Option[Projected[Polygon]]): ConnectionIO[Seq[MosaicDefinition]] = {

    def geom(stpWithFootprint: (SceneToProjectwithSceneType, Option[Projected[MultiPolygon]])) = stpWithFootprint._2.get.geom

    def maybeNotWorthless(
      coveredByGeomO: Option[MultiPolygon],
      targetCoverageO: Option[Polygon]
    )(pair: (SceneToProjectwithSceneType, Option[Projected[MultiPolygon]])): Boolean =
      (coveredByGeomO, targetCoverageO) match {
        case (_, None) => true
        case (None, Some(coveredSoFar)) => !geom(pair).coveredBy(coveredSoFar)
        case (Some(targetCoverage), Some(coveredSoFar)) => {
          !(geom(pair).coveredBy(coveredSoFar) || targetCoverage.coveredBy(coveredSoFar))
        }
      }

    val filters = List(
      polygonOption.map(polygon => fr"ST_Intersects(scenes.tile_footprint, ${polygon})"),
      Some(fr"scenes_to_projects.project_id = ${projectId}"),
      Some(fr"scenes.ingest_status = 'INGESTED'"),
      Some(fr"data_footprint IS NOT NULL")
    )

    val select = fr"""
    SELECT
      scene_id, project_id, accepted, scene_order, mosaic_definition, scene_type, ingest_location, data_footprint
    FROM
      scenes_to_projects
    LEFT JOIN
      scenes
    ON scenes.id = scenes_to_projects.scene_id
      """

    val countF = fr"""
    SELECT count(1)
    FROM
      scenes_to_projects
    LEFT JOIN
      scenes
    ON scenes.id = scenes_to_projects.scene_id
    """

    var coveredSoFar: Option[MultiPolygon] = None
    val targetGeom: Option[Polygon] = polygonOption map { _.geom }

    for {
      stpsWithFootprints <- {
        (select ++ whereAndOpt(filters: _*) ++ fr"ORDER BY scene_order, coalesce(acquisition_date, scenes.created_at) DESC").query[(SceneToProjectwithSceneType, Option[Projected[MultiPolygon]])]
          .stream
          .takeWhile(
            (p: (SceneToProjectwithSceneType, Option[Projected[MultiPolygon]])) => {
              val notWorthless = maybeNotWorthless(coveredSoFar, targetGeom)(p)
              coveredSoFar = Some(
                coveredSoFar.map(mp => (geom(p) union mp).asMultiPolygon.get).getOrElse(geom(p))
              )
              notWorthless
            }
          )
          .compile
          .toList
      }
      count <- (countF ++ whereAndOpt(filters: _*)).query[Int].unique
    } yield {
      logger.info(s"Using ${stpsWithFootprints.length} scenes in project out of ${count}")
      // "skimmed" in the sense that we're just taking the top layer of scenes for the mosaic
      // val skimmedStpsWithFootprints = stpsWithFootprints.filter(
      //   (pair: (SceneToProjectwithSceneType, Option[Projected[MultiPolygon]])) => !(geom(pair).isEmpty)
      // ) match {
      //   case Nil => Nil
      //   case h +: Nil => stpsWithFootprints
      //   case h +: t => {
      //     stpsWithFootprints.foldLeft(List(h)) {
      //       (acc: List[(SceneToProjectwithSceneType, Option[Projected[MultiPolygon]])], candidate: (SceneToProjectwithSceneType, Option[Projected[MultiPolygon]])) => {
      //         if(geom(candidate).coveredBy(geom(acc.last))) {
      //           acc
      //         } else {
      //           acc :+ (candidate._1, Some(Projected((geom(candidate) union geom(acc.last)).asMultiPolygon.get, 3857)))
      //         }
      //       }
      //     }

      //   }
      // }
      // skimmedStpsWithFootprints.asdf
      // logger.debug(s"Wound up with ${skimmedStpsWithFootprints.length} scenes that contribute -- SHOULD MATCH")
      // val md = MosaicDefinition.fromScenesToProjects(skimmedStpsWithFootprints map { _._1.get })
      val md = MosaicDefinition.fromScenesToProjects(stpsWithFootprints map { _._1 })
      logger.debug(s"Mosaic Definition: ${md}")
      md
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
}
