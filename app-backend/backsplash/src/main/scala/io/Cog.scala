package com.rasterfoundry.backsplash.io

import cats.data.OptionT
import cats.effect.{IO, Timer}
import cats.implicits._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.datamodel.{MosaicDefinition, SingleBandOptions}
import com.rf.azavea.backsplash.Color
import geotrellis.proj4.WebMercator
import geotrellis.raster.{CellSize, Raster, RasterExtent, io => _, _}
import geotrellis.raster.io.geotiff.AutoHigherResolution
import geotrellis.server.core.cog.CogUtils
import geotrellis.spark.{io => _}
import geotrellis.vector.{Extent, Polygon, Projected}

object Cog extends RollbarNotifier {

  /** Fetch a downsampled global tile of a tif. The target resolution is (for now) 1/16th
    * the native resolution of the image.
    */
  def fetchGlobalTile(mosaicDefinition: MosaicDefinition,
                      poly: Option[Projected[Polygon]],
                      redBand: Int,
                      greenBand: Int,
                      blueBand: Int): IO[MultibandTile] =
    for {
      tiff <- mosaicDefinition.ingestLocation map {
        CogUtils.fromUri(_)
      } getOrElse {
        throw UningestedScenesException(
          s"Scene ${mosaicDefinition.sceneId} has no ingest location")
      }
      // target a cellsize in an overview that would up being about a 256 / 256 tile
      downsampleFactor = (tiff.cols * tiff.rows / (256 * 256)) max 1
      cellSize = CellSize(tiff.cellSize.width * downsampleFactor,
                          tiff.cellSize.height * downsampleFactor)
      overview = CogUtils.closestTiffOverview(tiff,
                                              cellSize,
                                              AutoHigherResolution)
    } yield {
      val cropped = (poly map { polygon: Projected[Polygon] =>
        overview.crop(RasterExtent(polygon.envelope, cellSize)).tile
      } getOrElse overview.tile)
      cropped.subsetBands(redBand, greenBand, blueBand)
    }

  def fetchSingleBandCogTile(
      md: MosaicDefinition,
      zoom: Int,
      col: Int,
      row: Int,
      extent: Extent,
      singleBandOptions: SingleBandOptions.Params,
      rawSingleBandValues: Boolean): OptionT[IO, Raster[Tile]] = {
    val tileIO = for {
      _ <- IO.pure(
        logger.debug(
          s"Fetching single-band COG tile for scene ID ${md.sceneId}"))
      raster <- CogUtils.fetch(md.ingestLocation.getOrElse(
                                 throw UningestedScenesException(
                                   "Cannot fetch scene with no ingest location")
                               ),
                               zoom,
                               col,
                               row)
      histograms <- Avro.layerHistogram(md.sceneId)
    } yield {
      logger.debug(s"Retrieved Tile: ${raster.tile.dimensions}")
      val tile = raster.tile.bands.lift(singleBandOptions.band) getOrElse {
        throw SingleBandOptionsException("No band found in single-band options")
      }
      val histogram = histograms
        .lift(singleBandOptions.band) getOrElse {
        throw MetadataException("No histogram found for band")
      }

      rawSingleBandValues match {
        case false =>
          Color.colorSingleBandTile(tile, extent, histogram, singleBandOptions)
        case _ => Raster(tile, extent)
      }
    }
    OptionT(tileIO.attempt.map(_.toOption))
  }

  def fetchMultiBandCogTile(md: MosaicDefinition,
                            zoom: Int,
                            col: Int,
                            row: Int,
                            extent: Extent): OptionT[IO, Raster[Tile]] = {
    val tileIO = for {
      _ <- IO.pure(
        logger.debug(
          s"Fetching multi-band COG tile for scene ID ${md.sceneId}"))
      raster <- CogUtils.fetch(md.ingestLocation.getOrElse(
                                 "Cannot fetch scene with no ingest location"),
                               zoom,
                               col,
                               row)
      histograms <- Avro.layerHistogram(md.sceneId)
    } yield {
      val bandOrder = List(
        md.colorCorrections.redBand,
        md.colorCorrections.greenBand,
        md.colorCorrections.blueBand
      )
      val subsetBands = raster.tile.subsetBands(bandOrder)
      val subsetHistograms = bandOrder map histograms
      val colored =
        md.colorCorrections.colorCorrect(subsetBands, subsetHistograms).color
      Raster(colored, extent).resample(256, 256)
    }
    OptionT(tileIO.attempt.map(_.toOption))
  }
}
