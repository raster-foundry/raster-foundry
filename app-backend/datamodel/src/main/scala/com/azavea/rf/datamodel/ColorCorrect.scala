package com.azavea.rf.datamodel

import com.azavea.rf.datamodel.color._

import io.circe.generic.JsonCodec
import geotrellis.raster._
import geotrellis.raster.equalization.HistogramEqualization
import geotrellis.raster.histogram.Histogram
import org.apache.commons.math3.util.FastMath
import spire.syntax.cfor._

/**
  * Usage of Approximations.{pow | exp} functions can allow to speed up this function on 10 - 15ms.
  * We can consider these functions usages in case of real performance issues caused by a long color correction.
  */
object ColorCorrect {
  import functions.SaturationAdjust._
  import functions.SigmoidalContrast._
  // import functions.Approximations

  case class LayerClipping(redMin: Int, redMax: Int, greenMin: Int, greenMax: Int, blueMin: Int, blueMax: Int)
  sealed trait ClipValue
  case class ClipBounds(min: Int, max: Int) extends ClipValue
  case class MaybeClipBounds(maybeMin: Option[Int], maybeMax: Option[Int]) extends ClipValue
  case class ClippingParams(band: Int, bounds: ClipValue)

  @JsonCodec
  case class Params(
    redBand: Int, greenBand: Int, blueBand: Int,
    gamma: BandGamma,
    bandClipping: PerBandClipping,
    tileClipping: MultiBandClipping,
    sigmoidalContrast: SigmoidalContrast,
    saturation: Saturation,
    equalize: Equalization,
    autoBalance: AutoWhiteBalance
  ) {
    def getGamma: Map[Int, Option[Double]] = Map(0 -> gamma.redGamma, 1 -> gamma.greenGamma, 2 -> gamma.blueGamma)

    def reorderBands(tile: MultibandTile, hist: Seq[Histogram[Double]]): (MultibandTile, Array[Histogram[Double]]) =
      (tile.subsetBands(redBand, greenBand, blueBand), Array(hist(redBand), hist(greenBand), hist(blueBand)))

    def colorCorrect(tile: MultibandTile, hist: Seq[Histogram[Double]]): MultibandTile = {
      val (rgbTile, rgbHist) = reorderBands(tile, hist)
      ColorCorrect(rgbTile, rgbHist, this)
    }
  }

  @inline def normalizeAndClampAndGammaCorrectPerPixel(z: Int, oldMin: Int, oldMax: Int, newMin: Int, newMax: Int, gammaOpt: Option[Double]): Int = {
    if(isData(z)) {
      val dNew = newMax - newMin
      val dOld = oldMax - oldMin

      // When dOld is nothing (normalization is meaningless in this context), we still need to clamp
      if (dOld == 0) {
        val v = {
          if (z > newMax) newMax
          else if (z < newMin) newMin
          else z
        }

        gammaOpt match {
          case None => v
          case Some(gamma) => {
            clampColor {
              val gammaCorrection = 1 / gamma
              (255 * FastMath.pow(v / 255.0, gammaCorrection)).toInt
            }
          }
        }
      } else {
        val v = {
          val scaled = (((z - oldMin) * dNew) / dOld) + newMin

          if (scaled > newMax) newMax
          else if (scaled < newMin) newMin
          else scaled
        }

        gammaOpt match {
          case None => v
          case Some(gamma) => {
            clampColor {
              val gammaCorrection = 1 / gamma
              (255 * FastMath.pow(v / 255.0, gammaCorrection)).toInt
            }
          }
        }
      }
    } else z
  }

  val rgbBand =
    (specificBand: Option[Int], allBands: Option[Int], tileDefault: Int) =>
      specificBand.fold(allBands)(Some(_)).fold(Some(tileDefault))(x => Some(x))

  def complexColorCorrect(rgbTile: MultibandTile, chromaFactor: Option[Double])
                         (layerNormalizeArgs: Map[Int, ClipBounds], gammas: Map[Int, Option[Double]])
                         (sigmoidalContrast: SigmoidalContrast)
                         (colorCorrectArgs: Map[Int, MaybeClipBounds], tileClipping: MultiBandClipping): MultibandTile = {
    val (red, green, blue) = (rgbTile.band(0), rgbTile.band(1), rgbTile.band(2))
    val (gr, gg, gb) = (gammas(0), gammas(1), gammas(2))
    val (nred, ngreen, nblue) = (
      ArrayTile.alloc(rgbTile.cellType, rgbTile.cols, rgbTile.rows),
      ArrayTile.alloc(rgbTile.cellType, rgbTile.cols, rgbTile.rows),
      ArrayTile.alloc(rgbTile.cellType, rgbTile.cols, rgbTile.rows)
    )

    val ClipBounds(rmin, rmax) = layerNormalizeArgs(0)
    val ClipBounds(gmin, gmax) = layerNormalizeArgs(1)
    val ClipBounds(bmin, bmax) = layerNormalizeArgs(2)

    val (rclipMin, rclipMax, rnewMin, rnewMax) = (rmin, rmax, 0, 255)
    val (gclipMin, gclipMax, gnewMin, gnewMax) = (gmin, gmax, 0, 255)
    val (bclipMin, bclipMax, bnewMin, bnewMax) = (bmin, bmax, 0, 255)

    val sigmoidal: Double => Double =
      (sigmoidalContrast.enabled, sigmoidalContrast.alpha, sigmoidalContrast.beta) match {
        case (true, Some(a), Some(b)) => localTransform(rgbTile.cellType, a, b)
        case _ => identity
      }

    val (clipr, clipg, clipb): (Int => Int, Int => Int, Int => Int) = {
      val MaybeClipBounds(mrmin, mrmax) = colorCorrectArgs(0)
      val MaybeClipBounds(mgmin, mgmax) = colorCorrectArgs(1)
      val MaybeClipBounds(mbmin, mbmax) = colorCorrectArgs(2)

      val (mrclipMin, mrclipMax) = (rgbBand(mrmin, tileClipping.min, 0).get, rgbBand(mrmax, tileClipping.max, 255).get)
      val (mgclipMin, mgclipMax) = (rgbBand(mgmin, tileClipping.min, 0).get, rgbBand(mgmax, tileClipping.max, 255).get)
      val (mbclipMin, mbclipMax) = (rgbBand(mbmin, tileClipping.min, 0).get, rgbBand(mbmax, tileClipping.max, 255).get)

      @inline def clipBands(z: Int, min: Int, max: Int): Int = {
        if (isData(z) && z > max) 255
        else if (isData(z) && z < min) 0
        else z
      }

      (clipBands(_, mrclipMin, mrclipMax), clipBands(_, mgclipMin, mgclipMax), clipBands(_, mbclipMin, mbclipMax))
    }

    /** In this case for some reason with this func wrap it works faster ¯\_(ツ)_/¯ (it was micro benchmarked) */
    lazyWrapper {
      cfor(0)(_ < rgbTile.cols, _ + 1) { col =>
        cfor(0)(_ < rgbTile.rows, _ + 1) { row =>
          val (r, g, b) =
            (ColorCorrect.normalizeAndClampAndGammaCorrectPerPixel(red.get(col, row), rclipMin, rclipMax, rnewMin, rnewMax, gr),
              ColorCorrect.normalizeAndClampAndGammaCorrectPerPixel(green.get(col, row), gclipMin, gclipMax, gnewMin, gnewMax, gg),
              ColorCorrect.normalizeAndClampAndGammaCorrectPerPixel(blue.get(col, row), bclipMin, bclipMax, bnewMin, bnewMax, gb))

          val (nr, ng, nb) = chromaFactor match {
            case Some(cf) => {
              val (hue, chroma, luma) = RGBToHCLuma(r, g, b)
              val newChroma = scaleChroma(chroma, cf)
              val (nr, ng, nb) = HCLumaToRGB(hue, newChroma, luma)
              (nr, ng, nb)
            }

            case _ => (r, g, b)
          }

          nred.set(col, row, clipr(sigmoidal(nr).toInt))
          ngreen.set(col, row, clipg(sigmoidal(ng).toInt))
          nblue.set(col, row, clipb(sigmoidal(nb).toInt))
        }
      }
    }

    MultibandTile(nred, ngreen, nblue)
  }

  def apply(rgbTile: MultibandTile, rgbHist: Array[Histogram[Double]], params: Params): MultibandTile = {
    var _rgbTile = rgbTile
    var _rgbHist = rgbHist
    val gammas = params.getGamma
    if (params.equalize.enabled) {
      _rgbTile = HistogramEqualization(rgbTile, rgbHist)
      _rgbHist = _rgbTile.histogramDouble()
    }

    val layerRgbClipping = {
      val range = 1 until 255
      var isCorrected = true
      val iMaxMin: Array[(Int, Int)] = Array.ofDim(3)
      cfor(0)(_ < _rgbHist.length, _ + 1) { index =>
        val hst = _rgbHist(index)
        val imin = hst.minValue().map(_.toInt).getOrElse(0)
        val imax = hst.maxValue().map(_.toInt).getOrElse(255)
        iMaxMin(index) = (imin, imax)
        isCorrected &&= {
          if (range.contains(hst.minValue().map(_.toInt).getOrElse(0))) true
          else if (range.contains(hst.maxValue().map(_.toInt).getOrElse(255))) true
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

    val colorCorrectArgs: Map[Int, MaybeClipBounds] = Map(
      0 -> MaybeClipBounds(params.bandClipping.redMin, params.bandClipping.redMax),
      1 -> MaybeClipBounds(params.bandClipping.greenMin, params.bandClipping.greenMax),
      2 -> MaybeClipBounds(params.bandClipping.blueMin, params.bandClipping.blueMax)
    )

    complexColorCorrect(_rgbTile, params.saturation.saturation)(layerNormalizeArgs, gammas)(params.sigmoidalContrast)(colorCorrectArgs, params.tileClipping)
  }

  @inline def clampColor(z: Int): Int = {
    if (z < 0) 0
    else if (z > 255) 255
    else z
  }

  private def lazyWrapper[T](f: => T): T = f
}
