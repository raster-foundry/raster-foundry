package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.RenderableStore.ToRenderableStoreOps
import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.{
  BacksplashGeotiff,
  BacksplashImage,
  RenderableStore
}
import com.rasterfoundry.common._
import com.rasterfoundry.common.color.ColorCorrect
import com.rasterfoundry.database.{SceneDao, SceneToLayerDao}
import com.rasterfoundry.datamodel.{BandOverride, SingleBandOptions}

import cats.data.{NonEmptyList => NEL}
import cats.effect.{ContextShift, IO}
import com.colisweb.tracing.TracingContext
import com.typesafe.scalalogging.LazyLogging
import doobie._
import doobie.implicits._
import geotrellis.vector.{Polygon, Projected}

import java.net.URI
import java.util.UUID

class RenderableStoreImplicits(xa: Transactor[IO])(
    implicit contextShift: ContextShift[IO])
    extends ToRenderableStoreOps
    with LazyLogging {

  implicit val sceneCache = Cache.caffeineSceneCache
  implicit val projectLayerCache = Cache.caffeineProjectLayerCache

  private def prefixFromHttpsS3Path(url: String): String = {
    val path = URI.create(url).getPath
    val tail = path.split("/").drop(1)
    tail.take(tail.length - 1).mkString("/")
  }
  @SuppressWarnings(Array("OptionGet"))
  private def mosaicDefinitionToImage(mosaicDefinition: MosaicDefinition,
                                      bandOverride: Option[BandOverride],
                                      projId: UUID): BacksplashImage[IO] = {
    val singleBandOptions =
      mosaicDefinition.singleBandOptions flatMap {
        _.as[SingleBandOptions.Params].toOption
      }
    val sceneId = mosaicDefinition.sceneId
    val footprint = mosaicDefinition.footprint getOrElse {
      throw NoFootprintException
    }

    val subsetBands = if (mosaicDefinition.isSingleBand) {
      singleBandOptions map { sbo =>
        List(sbo.band)
      } getOrElse {
        throw SingleBandOptionsException(
          "Single band options must be specified for single band projects"
        )
      }
    } else {
      bandOverride map { ovr =>
        List(ovr.redBand, ovr.greenBand, ovr.blueBand)
      } getOrElse {
        List(
          mosaicDefinition.colorCorrections.redBand,
          mosaicDefinition.colorCorrections.greenBand,
          mosaicDefinition.colorCorrections.blueBand
        )
      }
    }

    val colorCorrectParameters = ColorCorrect.Params(
      0, // red
      1, // green
      2 // blue
    )

    mosaicDefinition.datasource match {
      case Config.publicData.landsat8DatasourceId
          if Config.publicData.enableMultiTiff =>
        Landsat8MultiTiffImage(
          sceneId,
          footprint,
          subsetBands,
          colorCorrectParameters,
          singleBandOptions,
          mosaicDefinition.projectId,
          projId,
          mosaicDefinition.mask,
          mosaicDefinition.metadataFiles.headOption map { uri =>
            s"s3://landsat-pds/${prefixFromHttpsS3Path(uri)}"
          } getOrElse { "" }
        )
      case Config.publicData.sentinel2DatasourceId
          if Config.publicData.enableMultiTiff =>
        Sentinel2MultiTiffImage(
          sceneId,
          footprint,
          subsetBands,
          colorCorrectParameters,
          singleBandOptions,
          mosaicDefinition.projectId,
          projId,
          mosaicDefinition.mask,
          mosaicDefinition.metadataFiles.headOption map { uri =>
            s"s3://sentinel-s2-l1c/${prefixFromHttpsS3Path(uri)}"
          } getOrElse { "" }
        )
      case Config.publicData.landsat45ThematicMapperDatasourceId |
          Config.publicData.landsat7ETMDatasourceId
          if Config.publicData.enableMultiTiff =>
        LandsatHistoricalMultiTiffImage(
          sceneId,
          footprint,
          subsetBands,
          colorCorrectParameters,
          singleBandOptions,
          mosaicDefinition.projectId,
          projId,
          mosaicDefinition.mask,
          mosaicDefinition.sceneName
        )
      case _ =>
        val ingestLocation = mosaicDefinition.ingestLocation getOrElse {
          throw UningestedScenesException(
            s"Scene ${sceneId} does not have an ingest location"
          )
        }

        BacksplashGeotiff(
          sceneId,
          mosaicDefinition.projectId,
          projId, // actually the layer ID
          ingestLocation,
          subsetBands,
          colorCorrectParameters,
          singleBandOptions,
          mosaicDefinition.mask,
          footprint,
          mosaicDefinition.sceneMetadataFields,
          xa
        )
    }
  }

  implicit def sceneStore: RenderableStore[SceneDao] =
    new RenderableStore[SceneDao] {
      def read(
          self: SceneDao,
          projId: UUID, // actually a scene id, but argument names have to match
          window: Option[Projected[Polygon]],
          bandOverride: Option[BandOverride],
          imageSubset: Option[NEL[UUID]],
          tracingContext: TracingContext[IO]): BacksplashMosaic = {
        val tags = Map("sceneId" -> projId.toString)
        tracingContext.childSpan("sceneStore.read", tags) use { childContext =>
          Cacheable.getSceneById(projId, window, xa, childContext) map {
            scene =>
              // We don't actually have a project, so just make something up
              val randomProjectId = UUID.randomUUID
              val ingestLocation = scene.ingestLocation getOrElse {
                throw UningestedScenesException(
                  s"Scene ${scene.id} does not have an ingest location")
              }
              val footprint = scene.dataFootprint getOrElse {
                throw NoFootprintException
              }
              val imageBandOverride = bandOverride map { ovr =>
                List(ovr.redBand, ovr.greenBand, ovr.blueBand)
              } getOrElse { List(0, 1, 2) }
              val colorCorrectParams =
                ColorCorrect.Params(0, 1, 2)
              logger.debug(s"Chosen color correction: ${colorCorrectParams}")
              (tracingContext,
               List(
                 BacksplashGeotiff(
                   scene.id,
                   randomProjectId,
                   randomProjectId,
                   ingestLocation,
                   imageBandOverride,
                   colorCorrectParams,
                   None, // no single band options ever
                   None, // not adding the mask here, since out of functional scope for md to image
                   footprint,
                   scene.metadataFields,
                   xa
                 )))
          }
        }
      }
    }

  implicit def layerStore: RenderableStore[SceneToLayerDao] =
    new RenderableStore[SceneToLayerDao] {
      // projId here actually refers to a layer -- but the argument names have to
      // match the typeclass we're providing evidence for
      def read(self: SceneToLayerDao,
               projId: UUID,
               window: Option[Projected[Polygon]],
               bandOverride: Option[BandOverride],
               imageSubset: Option[NEL[UUID]],
               tracingContext: TracingContext[IO]): BacksplashMosaic = {
        val tags = Map("projectId" -> projId.toString)
        tracingContext.childSpan("layerStore.read", tags) use { child =>
          for {
            mosaicDefinitions <- child.childSpan("getMosaicDefinitions", tags) use {
              _ =>
                SceneToLayerDao
                  .getMosaicDefinition(projId, window, bandOverride map {
                    _.redBand
                  }, bandOverride map {
                    _.greenBand
                  }, bandOverride map {
                    _.blueBand
                  }, imageSubset map {
                    _.toList
                  } getOrElse List.empty)
                  .transact(xa)
            }
          } yield {
            (tracingContext, mosaicDefinitions map { md =>
              mosaicDefinitionToImage(md, bandOverride, projId)
            })
          }
        }
      }
    }
}
