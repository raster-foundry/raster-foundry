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

    def withBands(red: Int, green: Int, blue: Int) =
      this.copy(redBand = red, greenBand = green, blueBand = blue)
  }

  @inline def normalizeAndClampAndGammaCorrectPerPixel(
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

      if (noDataValue
            .map(nd => (nd - pixelValue).abs < 0.00000001)
            .getOrElse(false)) {
        0
      } else if (dOld == 0) {
        // When dOld is nothing (normalipixelValueation is meaningless in this context), we still need to clamp
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

  def complexColorCorrect(rgbTile: MultibandTile,
                          noDataValue: Option[Double]): MultibandTile = {
    val (red, green, blue) = (rgbTile.band(0), rgbTile.band(1), rgbTile.band(2))
    val tileCellType = UByteConstantNoDataCellType
    val (nred, ngreen, nblue) = (
      ArrayTile.alloc(tileCellType, rgbTile.cols, rgbTile.rows),
      ArrayTile.alloc(tileCellType, rgbTile.cols, rgbTile.rows),
      ArrayTile.alloc(tileCellType, rgbTile.cols, rgbTile.rows)
    )

    val tileMin = 1
    val (rclipMin, rclipMax, rnewMin, rnewMax) = (0, 255, tileMin, 255)
    val (gclipMin, gclipMax, gnewMin, gnewMax) = (0, 255, tileMin, 255)
    val (bclipMin, bclipMax, bnewMin, bnewMax) = (0, 255, tileMin, 255)

    /** In this case for some reason with this func wrap it works faster ¯\_(ツ)_/¯ (it was micro benchmarked) */
    lazyWrapper {
      cfor(0)(_ < rgbTile.cols, _ + 1) { col =>
        cfor(0)(_ < rgbTile.rows, _ + 1) { row =>
          val (r, g, b) =
            (
              ColorCorrect.normalizeAndClampAndGammaCorrectPerPixel(
                red.getDouble(col, row),
                rclipMin,
                rclipMax,
                rnewMin,
                rnewMax,
                noDataValue
              ),
              ColorCorrect.normalizeAndClampAndGammaCorrectPerPixel(
                green.getDouble(col, row),
                gclipMin,
                gclipMax,
                gnewMin,
                gnewMax,
                noDataValue
              ),
              ColorCorrect.normalizeAndClampAndGammaCorrectPerPixel(
                blue.getDouble(col, row),
                bclipMin,
                bclipMax,
                bnewMin,
                bnewMax,
                noDataValue
              )
            )

          nred.set(col, row, r.toInt)
          ngreen.set(col, row, g.toInt)
          nblue.set(col, row, b.toInt)
        }
      }
    }

    MultibandTile(nred, ngreen, nblue)
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
        val imin = hst.minValue().map(_.toInt).getOrElse(0)
        val imax = hst.maxValue().map(_.toInt).getOrElse(255)
        logger.trace(s"Histogram Min/Max: ${imin}/${imax}")
        iMaxMin(index) = (imin, imax)
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

    val layerNormalizeArgs: Map[Int, ClipBounds] = Map(
      0 -> ClipBounds(layerRgbClipping.redMin, layerRgbClipping.redMax),
      1 -> ClipBounds(layerRgbClipping.greenMin, layerRgbClipping.greenMax),
      2 -> ClipBounds(layerRgbClipping.blueMin, layerRgbClipping.blueMax)
    )

    logger.trace(s"Layer Normalize Args: ${layerNormalizeArgs}")
    complexColorCorrect(_rgbTile, noDataValue)
  }

  @inline def clampColor(z: Int): Int = {
    if (z < 0) 0
    else if (z > 255) 255
    else z
  }

  private def lazyWrapper[T](f: => T): T = f
}
