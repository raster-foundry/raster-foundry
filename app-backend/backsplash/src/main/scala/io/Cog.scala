package com.azavea.rf.backsplash.io

import cats.data.OptionT
import cats.effect.{IO, Timer}
import cats.implicits._
import com.azavea.rf.backsplash.error._
import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.datamodel.{MosaicDefinition, SingleBandOptions}
import com.rf.azavea.backsplash.Color
import geotrellis.raster.{Raster, io => _, _}
import geotrellis.server.core.cog.CogUtils
import geotrellis.spark.{io => _}
import geotrellis.vector.Extent

object Cog extends RollbarNotifier {

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
