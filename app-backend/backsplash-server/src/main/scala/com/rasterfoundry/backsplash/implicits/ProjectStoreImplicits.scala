package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.ProjectStore.ToProjectStoreOps
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.database.{ProjectLayerDao, SceneDao, SceneToLayerDao}
import com.rasterfoundry.datamodel.{BandOverride, SingleBandOptions}
import com.rasterfoundry.common._
import com.rasterfoundry.common.color.ColorCorrect
import com.rasterfoundry.backsplash.{
  ProjectStore,
  BacksplashImage,
  BacksplashGeotiff
}

import cats.data.{NonEmptyList => NEL, OptionT}
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import doobie._
import doobie.implicits._
import geotrellis.vector.{Polygon, Projected}

import java.util.UUID

class ProjectStoreImplicits(xa: Transactor[IO])
    extends ToProjectStoreOps
    with LazyLogging {

  implicit val sceneCache = Cache.caffeineSceneCache

  @SuppressWarnings(Array("OptionGet"))
  private def mosaicDefinitionToImage(mosaicDefinition: MosaicDefinition,
                                      bandOverride: Option[BandOverride],
                                      projId: UUID): BacksplashGeotiff = {
    val singleBandOptions =
      mosaicDefinition.singleBandOptions flatMap {
        _.as[SingleBandOptions.Params].toOption
      }
    val sceneId = mosaicDefinition.sceneId
    val ingestLocation = mosaicDefinition.ingestLocation getOrElse {
      throw UningestedScenesException(
        s"Scene ${sceneId} does not have an ingest location"
      )
    }
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
      2, // blue
      mosaicDefinition.colorCorrections.gamma,
      mosaicDefinition.colorCorrections.bandClipping,
      mosaicDefinition.colorCorrections.tileClipping,
      mosaicDefinition.colorCorrections.sigmoidalContrast,
      mosaicDefinition.colorCorrections.saturation
    )

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
      mosaicDefinition.noDataValue
    )
  }

  implicit val sceneStore: ProjectStore[SceneDao] = new ProjectStore[SceneDao] {
    def read(
        self: SceneDao,
        projId: UUID, // actually a scene id, but argument names have to match
        window: Option[Projected[Polygon]],
        bandOverride: Option[BandOverride],
        imageSubset: Option[NEL[UUID]]): fs2.Stream[IO, BacksplashImage[IO]] = {
      for {
        scene <- fs2.Stream.eval {
          Cacheable.getSceneById(projId, window, xa)
        }
      } yield {
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
        val colorCorrectParams = ColorCorrect.paramsFromBandSpecOnly(0, 1, 2)
        logger.debug(s"Chosen color correction: ${colorCorrectParams}")
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
          scene.metadataFields.noDataValue
        )
      }
    }

    def getOverviewConfig(self: SceneDao, projId: UUID) = IO.pure {
      OverviewConfig.empty
    }
  }

  implicit val layerStore: ProjectStore[SceneToLayerDao] =
    new ProjectStore[SceneToLayerDao] {
      // projId here actually refers to a layer -- but the argument names have to
      // match the typeclass we're providing evidence for
      def read(self: SceneToLayerDao,
               projId: UUID,
               window: Option[Projected[Polygon]],
               bandOverride: Option[BandOverride],
               imageSubset: Option[NEL[UUID]])
        : fs2.Stream[IO, BacksplashImage[IO]] = {
        SceneToLayerDao.getMosaicDefinition(
          projId,
          window,
          bandOverride map { _.redBand },
          bandOverride map { _.greenBand },
          bandOverride map { _.blueBand },
          imageSubset map { _.toList } getOrElse List.empty) map { md =>
          mosaicDefinitionToImage(md, bandOverride, projId)
        } transact (xa)
      }

      def getOverviewConfig(self: SceneToLayerDao,
                            projId: UUID): IO[OverviewConfig] =
        (for {
          projLayer <- OptionT {
            ProjectLayerDao.getProjectLayerById(projId).transact(xa)
          }
          overviewLocation <- OptionT.fromOption[IO] {
            projLayer.overviewsLocation
          }
          minZoom <- OptionT.fromOption[IO] { projLayer.minZoomLevel }
        } yield
          OverviewConfig(Some(overviewLocation), Some(minZoom))).value map {
          case Some(conf) => conf
          case _          => OverviewConfig.empty
        }
    }
}
