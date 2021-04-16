package com.rasterfoundry.common.color
import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.{MultibandTile}
import io.circe.generic.JsonCodec

object ColorCorrect extends LazyLogging {
  // by default, choose 2nd and 98th percentile histogram clips.
  // The bins in the GeoTrellis histogram implementation are 0-indexed,
  // so 2nd and 98th is 1 and 97.
  // These defaults were chosen because they look nice
  val defaultLowerBound = 1
  val defaultUpperBound = 97

  @JsonCodec
  final case class Params(
      redBand: Int,
      greenBand: Int,
      blueBand: Int
  ) {
    def colorCorrect(
        tile: MultibandTile,
        hist: Seq[Histogram[Double]],
        noDataValue: Option[Double],
        lowerQuantile: Option[Int],
        upperQuantile: Option[Int]
    ): MultibandTile = {
      val indexedHist = hist.toIndexedSeq
      val rgbHist = Seq(redBand, greenBand, blueBand) map { indexedHist(_) }
      val bands = tile.bands.zip(rgbHist).map {
        case (rgbTile, histogram) =>
          val breaks = histogram.quantileBreaks(100)
          val (oldTrueMin, oldTrueMax) = (breaks(0), breaks(255))
          if (oldTrueMin < 0 || oldTrueMax > 255) {
            val oldMin =
              breaks(lowerQuantile.getOrElse(defaultLowerBound)).toInt
            val oldMax =
              breaks(upperQuantile.getOrElse(defaultUpperBound)).toInt
            rgbTile
              .withNoData(noDataValue)
              .mapIfSet { cell =>
                if (cell < oldMin) oldMin
                else if (cell > oldMax) oldMax
                else cell
              }
              .normalize(oldMin, oldMax, 1, 255)
          } else {
            rgbTile.withNoData(noDataValue)
          }
      }
      MultibandTile(bands)
    }
  }
}
