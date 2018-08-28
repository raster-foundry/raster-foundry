package com.azavea.rf.datamodel.color.functions

import geotrellis.raster.{ArrayTile, MultibandTile}
import org.apache.commons.math3.util.FastMath
import spire.syntax.cfor.cfor

/**
  * Usage of Approximations.{pow | exp} functions can allow to speed up this function on 10 - 15ms.
  * We can consider these functions usages in case of real performance issues caused by a long saturation adjust.
  */
object SaturationAdjust {
  def apply(rgbTile: MultibandTile, chromaFactor: Double): MultibandTile = {
    scaleTileChroma(rgbTile, chromaFactor)
  }

  /* Convert RGB to Hue, Chroma, Luma https://en.wikipedia.org/wiki/HSL_and_HSV#Lightness */
  def scaleTileChroma(rgbTile: MultibandTile,
                      chromaFactor: Double): MultibandTile = {
    val (red, green, blue) = (rgbTile.band(0), rgbTile.band(1), rgbTile.band(2))
    val (nred, ngreen, nblue) = (
      ArrayTile.alloc(rgbTile.cellType, rgbTile.cols, rgbTile.rows),
      ArrayTile.alloc(rgbTile.cellType, rgbTile.cols, rgbTile.rows),
      ArrayTile.alloc(rgbTile.cellType, rgbTile.cols, rgbTile.rows)
    )
    cfor(0)(_ < rgbTile.cols, _ + 1) { col =>
      cfor(0)(_ < rgbTile.rows, _ + 1) { row =>
        val (r, g, b) =
          (red.get(col, row), green.get(col, row), blue.get(col, row))
        val (hue, chroma, luma) = rgbToHcluma(r, g, b)
        val newChroma = scaleChroma(chroma, chromaFactor)
        val (nr, ng, nb) = hclumaToRgb(hue, newChroma, luma)
        nred.set(col, row, nr)
        ngreen.set(col, row, ng)
        nblue.set(col, row, nb)
      }
    }
    MultibandTile(nred, ngreen, nblue)
  }

  def ~=(x: Double, y: Double, precision: Double): Boolean = {
    (x - y).abs < precision
  }

  // See link for a detailed explanation of what is happening. Basically we are taking the
  // RGB cube and tilting it on its side so that the black -> white line runs vertically
  // up the center of the HCL cylinder, then flattening the cube down into a hexagon and then
  // pretending that that hexagon is actually a cylinder.
  // https://en.wikipedia.org/wiki/HSL_and_HSV#Lightness
  def rgbToHcluma(rByte: Int,
                  gByte: Int,
                  bByte: Int): (Double, Double, Double) = {
    // RGB come in as unsigned Bytes, but the transformation requires Doubles [0,1]
    val (r, g, b) = (rByte / 255d, gByte / 255d, bByte / 255d)
    val colors = List(r, g, b)
    val max = colors.max
    val min = colors.min
    val chroma = max - min
    val hueSextant = (chroma, max) match {
      case (0, _) =>
        0 // Technically, undefined, but we'll ignore this value in this case.
      case (_, x) if ~=(x, r, precision = 0.0001) =>
        ((g - b) / chroma.toDouble) % 6
      case (_, x) if ~=(x, g, precision = 0.0001) =>
        ((b - r) / chroma.toDouble) + 2
      case (_, x) if ~=(x, b, precision = 0.0001) =>
        ((r - g) / chroma.toDouble) + 4
    }
    // Wrap degrees
    val hue = (hueSextant * 60d) % 360 match {
      case h if h < 0  => h + 360
      case h if h >= 0 => h
    }
    // Perceptually weighted average of "lightness" contribution of sRGB primaries (it's
    // not clear that we're in sRGB here, but that's the default for most images intended for
    // display so it's a good guess in the absence of explicit information).
    val luma = 0.21 * r + 0.72 * g + 0.07 * b
    (hue, chroma, luma)
  }

  // Reverse the process above
  def hclumaToRgb(hue: Double,
                  chroma: Double,
                  luma: Double): (Int, Int, Int) = {
    val sextant = hue / 60d
    val X = chroma * (1 - math.abs((sextant % 2) - 1))
    // Projected color values, i.e., on the flat projection of the RGB cube
    val (rFlat: Double, gFlat: Double, bFlat: Double) =
      (chroma, sextant) match {
        case (0.0, _)                  => (0.0, 0.0, 0.0) // Gray (or black / white)
        case (_, s) if 0 <= s && s < 1 => (chroma, X, 0.0)
        case (_, s) if 1 <= s && s < 2 => (X, chroma, 0.0)
        case (_, s) if 2 <= s && s < 3 => (0.0, chroma, X)
        case (_, s) if 3 <= s && s < 4 => (0.0, X, chroma)
        case (_, s) if 4 <= s && s < 5 => (X, 0.0, chroma)
        case (_, s) if 5 <= s && s < 6 => (chroma, 0.0, X)
      }

    // We can add the same value to each component to move straight up the cylinder from the projected
    // plane, back to the correct lightness value.
    val lumaCorrection = luma - (0.21 * rFlat + 0.72 * gFlat + 0.07 * bFlat)
    val r = clamp8Bit((255 * (rFlat + lumaCorrection)).toInt)
    val g = clamp8Bit((255 * (gFlat + lumaCorrection)).toInt)
    val b = clamp8Bit((255 * (bFlat + lumaCorrection)).toInt)
    (r, g, b)
  }

  def scaleChroma(chroma: Double, scaleFactor: Double): Double = {
    // Chroma is a Double in the range [0.0, 1.0]. Scale factor is the same as our other gamma corrections:
    // a Double in the range [0.0, 2.0].
    val scaled = FastMath.pow(chroma, 1.0 / scaleFactor)
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
