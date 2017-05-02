package com.azavea.rf.datamodel

import io.circe._
import io.circe.syntax._
import io.circe.generic.JsonCodec

import spire.syntax.cfor._

import geotrellis.raster._
import geotrellis.raster.equalization.HistogramEqualization
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.sigmoidal.SigmoidalContrast
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._


object SaturationAdjust {
  def apply(rgbTile: MultibandTile, chromaFactor: Double): MultibandTile = {
    scaleTileChroma(rgbTile, chromaFactor)
  }

  /* Convert RGB to Hue, Chroma, Luma https://en.wikipedia.org/wiki/HSL_and_HSV#Lightness */
  def scaleTileChroma(rgbTile: MultibandTile, chromaFactor: Double): MultibandTile = {
    val (red, green, blue) = (rgbTile.band(0), rgbTile.band(1), rgbTile.band(2))
    val (nred, ngreen, nblue) = (
      ArrayTile.alloc(rgbTile.cellType, rgbTile.cols, rgbTile.rows),
      ArrayTile.alloc(rgbTile.cellType, rgbTile.cols, rgbTile.rows),
      ArrayTile.alloc(rgbTile.cellType, rgbTile.cols, rgbTile.rows)
    )
    cfor(0)(_ < rgbTile.cols, _ + 1) { col =>
      cfor(0)(_ < rgbTile.rows, _ + 1) { row =>
        val (r, g, b) = (red.get(col, row), green.get(col, row), blue.get(col, row))
        val (hue, chroma, luma) = RGBToHCLuma(r, g, b)
        val newChroma = scaleChroma(chroma, chromaFactor)
        val (nr, ng, nb) = HCLumaToRGB(hue, newChroma, luma)
        nred.set(col, row, nr)
        ngreen.set(col, row, ng)
        nblue.set(col, row, nb)
      }
    }
    MultibandTile(nred, ngreen, nblue)
  }

  // See link for a detailed explanation of what is happening. Basically we are taking the
  // RGB cube and tilting it on its side so that the black -> white line runs vertically
  // up the center of the HCL cylinder, then flattening the cube down into a hexagon and then
  // pretending that that hexagon is actually a cylinder.
  // https://en.wikipedia.org/wiki/HSL_and_HSV#Lightness
  def RGBToHCLuma(rByte: Int, gByte: Int, bByte: Int): (Double, Double, Double) = {
    // RGB come in as unsigned Bytes, but the transformation requires Doubles [0,1]
    val (r, g, b) = (rByte / 255.0, gByte / 255.0, bByte / 255.0)
    val colors = List(r, g, b)
    val max = colors.max
    val min = colors.min
    val chroma = max - min
    val hueSextant = ((chroma, max) match {
      case (0, _) => 0 // Technically, undefined, but we'll ignore this value in this case.
      case (_, x) if x == r => ((g - b) / chroma.toDouble) % 6
      case (_, x) if x == g => ((b - r) / chroma.toDouble) + 2
      case (_, x) if x == b => ((r - g) / chroma.toDouble) + 4
    })
    // Wrap degrees
    val hue = ((hueSextant * 60.0) % 360) match {
      case h if h < 0 => h + 360
      case h if h >= 0 => h
    }
    // Perceptually weighted average of "lightness" contribution of sRGB primaries (it's
    // not clear that we're in sRGB here, but that's the default for most images intended for
    // display so it's a good guess in the absence of explicit information).
    val luma = 0.21*r + 0.72*g + 0.07*b
    (hue, chroma, luma)
  }

  // Reverse the process above
  def HCLumaToRGB(hue: Double, chroma: Double, luma: Double): (Int, Int, Int) = {
    val sextant = hue / 60.0
    val X = chroma * (1 - Math.abs((sextant % 2) - 1))
    // Projected color values, i.e., on the flat projection of the RGB cube
    val (rFlat:Double, gFlat:Double, bFlat:Double) = ((chroma, sextant) match {
      case (0.0, _) => (0.0, 0.0, 0.0) // Gray (or black / white)
      case (_, s) if 0 <= s && s < 1 => (chroma, X, 0.0)
      case (_, s) if 1 <= s && s < 2 => (X, chroma, 0.0)
      case (_, s) if 2 <= s && s < 3 => (0.0, chroma, X)
      case (_, s) if 3 <= s && s < 4 => (0.0, X, chroma)
      case (_, s) if 4 <= s && s < 5 => (X, 0.0, chroma)
      case (_, s) if 5 <= s && s < 6 => (chroma, 0.0, X)
    })

    // We can add the same value to each component to move straight up the cylinder from the projected
    // plane, back to the correct lightness value.
    val lumaCorrection = luma - (0.21*rFlat + 0.72*gFlat + 0.07*bFlat)
    val r = clamp8Bit((255 * (rFlat + lumaCorrection)).toInt)
    val g = clamp8Bit((255 * (gFlat + lumaCorrection)).toInt)
    val b = clamp8Bit((255 * (bFlat + lumaCorrection)).toInt)
    (r, g, b)
  }

  def scaleChroma(chroma: Double, scaleFactor: Double): Double = {
    // Chroma is a Double in the range [0.0, 1.0]. Scale factor is the same as our other gamma corrections:
    // a Double in the range [0.0, 2.0].
    val scaled = math.pow(chroma, 1.0 / scaleFactor)
    if (scaled < 0.0) 0.0
    else if (scaled > 1.0) 1.0
    else scaled
  }

  @inline def clamp8Bit(z: Int): Int = {
    if (z < 0) 0
    else if (z > 255) 255
    else z
  }
}

object ColorCorrect {

  @JsonCodec
  case class Params(
    redBand: Int, greenBand: Int, blueBand: Int,
    redGamma: Option[Double], greenGamma: Option[Double], blueGamma: Option[Double],
    redMax: Option[Int], greenMax: Option[Int], blueMax: Option[Int],
    redMin: Option[Int], greenMin: Option[Int], blueMin: Option[Int],
    contrast: Option[Double], brightness: Option[Int],
    alpha: Option[Double], beta: Option[Double],
    min: Option[Int], max: Option[Int],
    saturation: Option[Double],
    equalize: Boolean
  ) {
    def reorderBands(tile: MultibandTile, hist: Seq[Histogram[Double]]): (MultibandTile, Array[Histogram[Double]]) =
      (tile.subsetBands(redBand, greenBand, blueBand), Array(hist(redBand), hist(greenBand), hist(blueBand)))

    def colorCorrect(tile: MultibandTile, hist: Seq[Histogram[Double]]): MultibandTile = {
      val (rgbTile, rgbHist) = reorderBands(tile, hist)
      ColorCorrect(rgbTile, rgbHist, this)
    }
  }

  object Params {
    def colorCorrectParams: Directive1[Params] =
      parameters(
        'redBand.as[Int].?(0), 'greenBand.as[Int].?(1), 'blueBand.as[Int].?(2),
        "redGamma".as[Double].?, "greenGamma".as[Double].?, "blueGamma".as[Double].?,
        "redMax".as[Int].?, "greenMax".as[Int].?, "blueMax".as[Int].?,
        "redMin".as[Int].?, "greenMin".as[Int].?, "blueMin".as[Int].?,
        "contrast".as[Double].?, "brightness".as[Int].?,
        'alpha.as[Double].?, 'beta.as[Double].?,
        "min".as[Int].?, "max".as[Int].?,
        "saturation".as[Double].?,
        'equalize.as[Boolean].?(false)
      ).as(ColorCorrect.Params.apply _)
  }

  def apply(rgbTile: MultibandTile, rgbHist: Array[Histogram[Double]], params: Params): MultibandTile = {
    val maybeEqualize =
      if (params.equalize) Some(HistogramEqualization(_: MultibandTile, rgbHist)) else None

    val rgbBand =
      (specificBand:Option[Int], allBands:Option[Int], tileDefault: Int) =>
        specificBand.fold(allBands)(Some(_)).fold(Some(tileDefault))(x => Some(x))
    
    case class ClippingParams(band: Int, min: Option[Int], max: Option[Int])
    val rgbBandArgs = (
      ClippingParams(0, params.redMin, params.redMax)
        :: ClippingParams(1, params.greenMin, params.greenMax)
        :: ClippingParams(2, params.blueMin, params.blueMax)
        :: Nil
    )

    val normalizeAndClampValues =
      (_:MultibandTile).mapBands { (i, tile) =>
        val maybeNewTile = for {
          args <- rgbBandArgs.find(cp => cp.band == i)
          nmax <- rgbBand(args.max, params.max, maxCellValue(tile.cellType))
          nmin <- rgbBand(args.min, params.min, 0)
        } yield normalizeAndClamp(tile, oldMin = nmin, oldMax = nmax, newMin = 0, newMax = 255).convert(UByteConstantNoDataCellType)
        
        maybeNewTile.getOrElse(tile)
      }

    val maybeAdjustBrightness =
      for (b <- params.brightness)
        yield (mb: MultibandTile) => mb.mapBands { (i, tile) => adjustBrightness(tile, b) }

    val maybeAdjustContrast =
      for (c <- params.contrast)
        yield (mb: MultibandTile) => mb.mapBands { (i, tile) => adjustContrast(tile, c) }

    val maybeAdjustGamma =
      for (c <- params.contrast)
        yield (_: MultibandTile).mapBands { (i, tile) =>
          i match {
            case 0 =>
              params.redGamma.fold(tile)(gamma => gammaCorrect(tile, gamma))
            case 1 =>
              params.greenGamma.fold(tile)(gamma => gammaCorrect(tile, gamma))
            case 2 =>
              params.blueGamma.fold(tile)(gamma => gammaCorrect(tile, gamma))
            case _ =>
              sys.error("Too many bands")
          }
        }

    val maybeSigmoidal =
      for (alpha <- params.alpha; beta <- params.beta)
        yield SigmoidalContrast(_: MultibandTile, alpha, beta)

    val maybeAdjustSaturation =
      for (saturationFactor <- params.saturation)
        yield SaturationAdjust(_: MultibandTile, saturationFactor)


    // Sequence of transformations to tile, flatten removes None from the list
    val transformations: List[MultibandTile => MultibandTile] = List(
      maybeEqualize,
      Some(normalizeAndClampValues),
      maybeAdjustBrightness,
      maybeAdjustGamma,
      maybeAdjustContrast,
      maybeAdjustSaturation,
      maybeSigmoidal
    ).flatten

    // Apply tile transformations in order from left to right
    transformations.foldLeft(rgbTile){ (t, f) => f(t) }
  }

  @inline def clampColor(z: Int): Int = {
    if (z < 0) 0
    else if (z > 255) 255
    else z
  }

  def maxCellValue(ct: CellType): Int =
    ct match {
      case _: FloatCells =>
        Float.MaxValue.toInt
      case _: DoubleCells =>
        Double.MaxValue.toInt
      case _: BitCells | _: UByteCells | _: UShortCells =>
        (1 << ct.bits) - 1
      case _: ByteCells | _: ShortCells | _: IntCells =>
        ((1 << ct.bits) - 1) - (1 << (ct.bits - 1))
    }

  def normalizeAndClamp(tile: Tile, oldMin: Int, oldMax: Int, newMin: Int, newMax: Int): Tile = {
    val dNew = newMax - newMin
    val dOld = oldMax - oldMin

    // When dOld is nothing (normalization is meaningless in this context), we still need to clamp
    if (dOld == 0) tile.mapIfSet { z =>
      if (z > newMax) newMax
      else if (z < newMin) newMin
      else z
    } else tile.mapIfSet { z =>
      val scaled = (((z - oldMin) * dNew) / dOld) + newMin

      if (scaled > newMax) newMax
      else if (scaled < newMin) newMin
      else scaled
    }
  }

  def adjustBrightness(tile: Tile, brightness: Int): Tile =
    tile.mapIfSet { z =>
      clampColor(if (z > 0) z + brightness else z)
    }


  def adjustContrast(tile: Tile, contrast: Double): Tile =
    tile.mapIfSet { z =>
      clampColor {
        val contrastFactor = (259 * (contrast + 255)) / (255 * (259 - contrast))
        ((contrastFactor * (z - 128)) + 128).toInt
      }
    }

  def gammaCorrect(tile: Tile, gamma: Double): Tile =
    tile.mapIfSet { z =>
      clampColor {
        val gammaCorrection = 1 / gamma
        (255 * math.pow(z / 255.0, gammaCorrection)).toInt
      }
    }
}
