package com.rasterfoundry.database

import com.rasterfoundry.common._
import com.rasterfoundry.common.color.ColorCorrect
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util.Cache

import cats.Applicative
import cats.data.{NonEmptyList => NEL}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import doobie.Fragments._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import geotrellis.vector.{MultiPolygon, Polygon, Projected}
import scalacache.CatsEffect.modes._
import scalacache._

import scala.concurrent.duration._

import java.util.UUID

@SuppressWarnings(Array("EmptyCaseClass"))
final case class SceneToLayerDao()

object SceneToLayerDao extends Dao[SceneToLayer] with LazyLogging {

  import Cache.MosaicDefinitionCache._

  val tableName = "scenes_to_layers"

  val selectF: Fragment = sql"""
    SELECT
      scene_id, project_layer_id, accepted, scene_order, mosaic_definition
    FROM
  """ ++ tableF

  def mosaicDefCacheKey(projectLayerId: UUID): String =
    s"SceneToLayer:ProjectLayer:$projectLayerId:MultiTiff:${Config.publicData.enableMultiTiff}:binary"

  def deleteMosaicDefCache(projectLayerId: UUID): ConnectionIO[Unit] = {
    for {
      _ <- {
        val cacheKey = mosaicDefCacheKey(projectLayerId)
        logger.debug(s"Removing $cacheKey")
        remove(cacheKey)(mosaicDefinitionCache, async[ConnectionIO]).attempt
      }
    } yield ()
  }

  def acceptScene(projectLayerId: UUID, sceneId: UUID): ConnectionIO[Int] = {
    for {
      acceptCount <- fr"""
      UPDATE scenes_to_layers
      SET accepted = true
      WHERE project_layer_id = ${projectLayerId}
        AND scene_id = ${sceneId}
        """.update.run
      _ <- deleteMosaicDefCache(projectLayerId)
    } yield acceptCount
  }

  def acceptScenes(
      projectLayerId: UUID,
      sceneIds: List[UUID]
  ): ConnectionIO[Int] = {
    for {
      updateCount <- sceneIds.toNel match {
        case Some(ids) => acceptScenes(projectLayerId, ids)
        case _         => 0.pure[ConnectionIO]
      }
      _ <- updateCount match {
        case 0 => ().pure[ConnectionIO]
        case _ => deleteMosaicDefCache(projectLayerId)
      }
    } yield updateCount
  }

  def acceptScenes(
      projectLayerId: UUID,
      sceneIds: NEL[UUID]
  ): ConnectionIO[Int] = {
    for {
      updateCount <- (
        fr"""
        UPDATE scenes_to_layers
        SET accepted = true
      """ ++ Fragments.whereAnd(
          fr"project_layer_id = ${projectLayerId}",
          Fragments.in(fr"scene_id", sceneIds)
        )
      ).update.run
      _ <- deleteMosaicDefCache(projectLayerId)
    } yield updateCount
  }

  def setManualOrder(
      projectLayerId: UUID,
      sceneIds: Seq[UUID]
  ): ConnectionIO[Seq[UUID]] = {
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
      _ <- deleteMosaicDefCache(projectLayerId)
    } yield {
      sceneIds
    }
  }

  def getMosaicDefinition(projectLayerId: UUID,
                          polygonOption: Option[Projected[Polygon]],
                          redBand: Option[Int] = None,
                          greenBand: Option[Int] = None,
                          blueBand: Option[Int] = None,
                          sceneIdSubset: List[UUID] = List.empty)
    : ConnectionIO[List[MosaicDefinition]] = {

    val cacheKey = mosaicDefCacheKey(projectLayerId)

    val ingestFilter: Option[Fragment] =
      if (Config.publicData.enableMultiTiff) {
        Some(
          Fragment.const(s"""
          | (ingest_status = 'INGESTED' OR datasource IN
          | ('${Config.publicData.landsat8DatasourceId}' :: uuid,
          |  '${Config.publicData.sentinel2DatasourceId}' :: uuid,
          |  '${Config.publicData.landsat45ThematicMapperDatasourceId}' :: uuid,
          |  '${Config.publicData.landsat7ETMDatasourceId}' :: uuid))
          | """.trim.stripMargin)
        )
      } else {
        Some(fr"ingest_status = 'INGESTED'")
      }

    val dbQueryFilters = List(
      Some(fr"project_layer_id = ${projectLayerId}"),
      Some(fr"accepted = true"),
      ingestFilter
    )

    val orderByF: Fragment =
      fr"ORDER BY scene_order ASC NULLS LAST, (acquisition_date, cloud_cover) ASC"

    val select =
      fr"""
    SELECT
      scene_id, project_id, datasource, scenes_stl.name, project_layer_id, accepted, scene_order,
      mosaic_definition,
      scene_type, ingest_location, data_footprint, is_single_band, single_band_options,
      geometry, data_path, crs, band_count, cell_type, grid_extent, resolutions, no_data_value, metadata_files
    FROM (
      scenes_to_layers
    LEFT JOIN
      scenes
    ON scenes.id = scenes_to_layers.scene_id
    ) scenes_stl
    LEFT JOIN
      project_layers
    ON
      scenes_stl.project_layer_id = project_layers.id
      """
    for {
      queryResult <- {
        cachingF(cacheKey)(Some(30 minutes)) {
          logger.debug(s"Cache Miss: $cacheKey - hitting database")
          (select ++ whereAndOpt(dbQueryFilters: _*) ++ orderByF)
            .query[SceneToLayerWithSceneType]
            .to[List]
        } map { results =>
          results.filter { stl =>
            ((stl.dataFootprint, polygonOption) match {
              case (Some(footprint), Some(polygon)) =>
                footprint.intersects(polygon)
              case (None, Some(_)) => false
              case _               => true
            }) &&
            (sceneIdSubset match {
              case Nil => true
              case _   => sceneIdSubset.contains(stl.sceneId)
            })
          }
        }
      }
    } yield {
      queryResult map { stp =>
        {
          Applicative[Option].map3(redBand, greenBand, blueBand) {
            case (r, g, b) =>
              MosaicDefinition(
                stp.sceneId,
                stp.projectId,
                stp.datasource,
                stp.sceneName,
                stp.colorCorrectParams.copy(
                  redBand = r,
                  greenBand = g,
                  blueBand = b
                ),
                stp.sceneType,
                stp.ingestLocation,
                stp.dataFootprint flatMap {
                  _.geom.as[MultiPolygon]
                },
                stp.isSingleBand,
                stp.singleBandOptions,
                stp.mask flatMap {
                  _.geom.as[MultiPolygon]
                },
                stp.metadataFields,
                stp.metadataFiles
              )
          } getOrElse {
            MosaicDefinition(
              stp.sceneId,
              stp.projectId,
              stp.datasource,
              stp.sceneName,
              stp.colorCorrectParams,
              stp.sceneType,
              stp.ingestLocation,
              stp.dataFootprint flatMap {
                _.geom.as[MultiPolygon]
              },
              stp.isSingleBand,
              stp.singleBandOptions,
              stp.mask flatMap {
                _.as[MultiPolygon]
              },
              stp.metadataFields,
              stp.metadataFiles
            )
          }
        }
      }
    }
  }

  def getMosaicDefinition(
      projectLayerId: UUID
  ): ConnectionIO[List[MosaicDefinition]] = {
    getMosaicDefinition(projectLayerId, None)
  }

  def getColorCorrectParams(
      projectLayerId: UUID,
      sceneId: UUID
  ): ConnectionIO[ColorCorrect.Params] = {
    query
      .filter(
        fr"project_layer_id = ${projectLayerId} AND scene_id = ${sceneId}"
      )
      .select
      .map { stl: SceneToLayer =>
        stl.colorCorrectParams
      }
  }

  def setColorCorrectParams(
      projectLayerId: UUID,
      sceneId: UUID,
      colorCorrectParams: ColorCorrect.Params
  ): ConnectionIO[SceneToLayer] = {
    fr"""
      UPDATE scenes_to_layers
      SET mosaic_definition = ${colorCorrectParams}
      WHERE project_layer_id = ${projectLayerId} AND scene_id = ${sceneId}
    """.update.withUniqueGeneratedKeys(
      "scene_id",
      "project_layer_id",
      "accepted",
      "scene_order",
      "mosaic_definition"
    )
  }

  def setColorCorrectParamsBatch(
      projectLayerId: UUID,
      batchParams: BatchParams
  ): ConnectionIO[List[SceneToLayer]] = {
    for {
      stlList <- batchParams.items
        .map(
          params =>
            setColorCorrectParams(projectLayerId, params.sceneId, params.params)
        )
        .sequence
      _ <- deleteMosaicDefCache(projectLayerId)
    } yield stlList
  }

  def setProjectLayerColorBands(
      projectLayerId: UUID,
      colorBands: ProjectColorModeParams
  ): ConnectionIO[Int] = {
    // TODO support setting color band by datasource instead of project wide
    // if there is not a mosaic definition at this point, then the scene_to_project row was not created correctly
    for {
      updateCount <- (fr"""
    UPDATE scenes_to_layers
    SET mosaic_definition =
      (mosaic_definition ||
        json_build_object(
          'redBand',${colorBands.redBand},
          'blueBand',${colorBands.blueBand},
          'greenBand', ${colorBands.greenBand}
        )::jsonb
      )
    WHERE project_layer_id = ${projectLayerId}
    """).update.run
      _ <- deleteMosaicDefCache(projectLayerId)
    } yield updateCount
  }

  def getProjectsAndLayersBySceneId(
      sceneId: UUID
  ): ConnectionIO[List[SceneWithProjectIdLayerId]] = {
    (fr"""
      SELECT scene_id, project_id, project_layer_id
      FROM (
        scenes_to_layers
      LEFT JOIN
        scenes
      ON scenes.id = scenes_to_layers.scene_id
      ) scenes_stl
      LEFT JOIN
        project_layers
      ON
        scenes_stl.project_layer_id = project_layers.id
      WHERE scene_id = $sceneId
    """).query[SceneWithProjectIdLayerId].to[List]
  }
}
