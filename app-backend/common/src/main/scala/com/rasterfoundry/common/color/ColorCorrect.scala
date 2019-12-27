package com.rasterfoundry.common.color
import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.{ArrayTile, MultibandTile, isData}
import io.circe.generic.JsonCodec
import spire.syntax.cfor.cfor
import geotrellis.raster.UByteConstantNoDataCellType

/**
  * Usage of Approximations.{pow | exp} functions can allow to speed up this function on 10 - 15ms.
  * We can consider these functions usages in case of real performance issues caused by a long color correction.
  */
object ColorCorrect extends LazyLogging {

  final case class LayerClipping(
      redMin: Int,
      redMax: Int,
      greenMin: Int,
      greenMax: Int,
      blueMin: Int,
      blueMax: Int
  )
  sealed trait ClipValue
  final case class ClipBounds(min: Int, max: Int) extends ClipValue
  final case class MaybeClipBounds(maybeMin: Option[Int], maybeMax: Option[Int])
      extends ClipValue
  final case class ClippingParams(band: Int, bounds: ClipValue)

  @JsonCodec
  final case class Params(
      redBand: Int,
      greenBand: Int,
      blueBand: Int
  ) {
    def colorCorrect(
        tile: MultibandTile,
        hist: Seq[Histogram[Double]],
        nodataValue: Option[Double]
    ): MultibandTile = {
      val indexedHist = hist.toIndexedSeq
      val rgbHist = Seq(redBand, greenBand, blueBand) map { indexedHist(_) }
      ColorCorrect(tile, rgbHist, nodataValue)
    }
  }

  @inline def normalizePerPixel(
      pixelValue: Double,
      oldMin: Int,
      oldMax: Int,
      newMin: Int,
      newMax: Int,
      noDataValue: Option[Double]
  ): Int = {
    if (isData(pixelValue)) {
      val dNew = newMax - newMin
      val dOld = oldMax - oldMin

      if (noDataValue.exists(nd => (nd - pixelValue).abs < 0.00000001)) {
        0
      } else if (dOld == 0) {
        // When dOld is nothing we still need to clamp
        val v = {
          if (pixelValue > newMax) newMax
          else if (pixelValue < newMin) newMin
          else pixelValue.toInt
        }

        v
      } else {
        val v = {
          val scaled = (((pixelValue - oldMin) * dNew) / dOld) + newMin

          if (scaled > newMax) newMax
          else if (scaled < newMin) newMin
          else scaled.toInt
        }

        v
      }
    } else pixelValue.toInt
  }

  val rgbBand: (Option[Int], Option[Int], Int) => Some[Int] =
    (specificBand: Option[Int], allBands: Option[Int], tileDefault: Int) =>
      specificBand.fold(allBands)(Some(_)).fold(Some(tileDefault))(x => Some(x))

  def normalize(rgbTile: MultibandTile,
                layerNormalizeArgs: Map[String, ClipBounds],
                noDataValue: Option[Double]): MultibandTile = {
    val (red, green, blue) = (rgbTile.band(0), rgbTile.band(1), rgbTile.band(2))
    val tileCellType = UByteConstantNoDataCellType
    val (dstRed, dstGreen, dstBlue) = (
      ArrayTile.alloc(tileCellType, rgbTile.cols, rgbTile.rows),
      ArrayTile.alloc(tileCellType, rgbTile.cols, rgbTile.rows),
      ArrayTile.alloc(tileCellType, rgbTile.cols, rgbTile.rows)
    )

    /** These are all super unsafe destructurings
      * I tried to make the assumptions more explicit by naming the keys instead
      * of numbering them, but if you see this and feel nervous about it, that's
      * reasonable.
      * It's safe, because the layerNormalizeArgs are constructed and consumed in
      * this and only this file, so there's no way for this map to get all wonky,
      * but, future reader, your concern is not unreasonable.
      */
    val ClipBounds(rmin, rmax) = layerNormalizeArgs("red")
    val ClipBounds(gmin, gmax) = layerNormalizeArgs("green")
    val ClipBounds(bmin, bmax) = layerNormalizeArgs("blue")

    val tileMin = 1
    val (rclipMin, rclipMax, rnewMin, rnewMax) = (rmin, rmax, tileMin, 255)
    val (gclipMin, gclipMax, gnewMin, gnewMax) = (gmin, gmax, tileMin, 255)
    val (bclipMin, bclipMax, bnewMin, bnewMax) = (bmin, bmax, tileMin, 255)

    /** In this case for some reason with this func wrap it works faster ¯\_(ツ)_/¯ (it was micro benchmarked) */
    lazyWrapper {
      cfor(0)(_ < rgbTile.cols, _ + 1) { col =>
        cfor(0)(_ < rgbTile.rows, _ + 1) { row =>
          val (r, g, b) =
            (
              ColorCorrect.normalizePerPixel(
                red.getDouble(col, row),
                rclipMin,
                rclipMax,
                rnewMin,
                rnewMax,
                noDataValue
              ),
              ColorCorrect.normalizePerPixel(
                green.getDouble(col, row),
                gclipMin,
                gclipMax,
                gnewMin,
                gnewMax,
                noDataValue
              ),
              ColorCorrect.normalizePerPixel(
                blue.getDouble(col, row),
                bclipMin,
                bclipMax,
                bnewMin,
                bnewMax,
                noDataValue
              )
            )

          dstRed.set(col, row, r.toInt)
          dstGreen.set(col, row, g.toInt)
          dstBlue.set(col, row, b.toInt)
        }
      }
    }

    MultibandTile(dstRed, dstGreen, dstBlue)
  }

  def apply(rgbTile: MultibandTile,
            rgbHist: Seq[Histogram[Double]],
            noDataValue: Option[Double]): MultibandTile = {
    val _rgbTile = rgbTile
    val _rgbHist = rgbHist

    val layerRgbClipping = {
      val range = 1 until 255
      var isCorrected = true
      val iMaxMin: Array[(Int, Int)] = Array.ofDim(3)
      cfor(0)(_ < _rgbHist.length, _ + 1) { index =>
        val hst = _rgbHist(index)
        val statsOption = hst.statistics()
        val imin = hst.minValue().map(_.toInt).getOrElse(0)
        val imax = hst.maxValue().map(_.toInt).getOrElse(255)

        // if data is a byte raster (0, 255) don't do anything
        // else if stats are available, clip assuming a normal distribution
        // else use the histogram's min/max
        (imin, imax, statsOption) match {
          case (0, 255, _)         => iMaxMin(index) = (0, 255)
          case (_, _, Some(stats)) =>
            // assuming a normal distribution, clips 2nd and 98th percentiles of values
            val newMin = stats.mean + (stats.stddev * -2.05)
            val newMax = stats.mean + (stats.stddev * 2.05)
            // assume non-negative values, otherwise visualization is weird
            // I think this happens because the distribution is non-normal
            iMaxMin(index) = (if (newMin < 0) 0 else newMin.toInt, newMax.toInt)
          case (min, max, _) => iMaxMin(index) = (min, max)
        }
        logger.trace(s"Histogram Min/Max: ${iMaxMin(index)}")

        isCorrected = {
          if (range.contains(imin) && range.contains(imax)) true
          else false
        }
      }

      if (!isCorrected) {
        val (rmin, rmax) = iMaxMin(0)
        val (gmin, gmax) = iMaxMin(1)
        val (bmin, bmax) = iMaxMin(2)
        LayerClipping(rmin, rmax, gmin, gmax, bmin, bmax)
      } else LayerClipping(0, 255, 0, 255, 0, 255)
    }

    val layerNormalizeArgs: Map[String, ClipBounds] = Map(
      "red" -> ClipBounds(layerRgbClipping.redMin, layerRgbClipping.redMax),
      "green" -> ClipBounds(layerRgbClipping.greenMin,
                            layerRgbClipping.greenMax),
      "blue" -> ClipBounds(layerRgbClipping.blueMin, layerRgbClipping.blueMax)
    )

    logger.trace(s"Layer Normalize Args: ${layerNormalizeArgs}")
    normalize(_rgbTile, layerNormalizeArgs, noDataValue)
  }

  @inline def clampColor(z: Int): Int = {
    if (z < 0) 0
    else if (z > 255) 255
    else z
  }

  private def lazyWrapper[T](f: => T): T = f
}
