package com.azavea.rf.tile.image

import geotrellis.raster._
import geotrellis.raster.equalization.HistogramEqualization
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.sigmoidal.SigmoidalContrast

object ColorCorrect {
  case class Params(
    redBand: Int, greenBand: Int, blueBand: Int,
    redGamma: Option[Double], greenGamma: Option[Double], blueGamma: Option[Double],
    contrast: Option[Double], brightness: Option[Int],
    alpha: Option[Double], beta: Option[Double],
    min: Option[Int], max: Option[Int],
    equalize: Boolean
  ) {
    def reorderBands(tile: MultibandTile, hist: Seq[Histogram[Double]]): (MultibandTile, Array[Histogram[Double]]) =
      (tile.subsetBands(redBand, greenBand, blueBand), Array(hist(redBand), hist(greenBand), hist(blueBand)))
  }

  def apply(rgbTile: MultibandTile, rgbHist: Array[Histogram[Double]], params: Params): MultibandTile = {
    val maybeEqualize =
      if (params.equalize) Some(HistogramEqualization(_: MultibandTile, rgbHist)) else None

    val normalizeAndClampValues =
      (_: MultibandTile).mapBands { (i, tile) =>
        normalizeAndClamp(tile,
          oldMin = params.min.getOrElse(0),
          oldMax = params.max.getOrElse(maxCellValue(tile.cellType)),
          newMin = 0,
          newMax = 255
        ).convert(UByteCellType)
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


    // Sequence of transformations to tile, flatten removes None from the list
    val transformations: List[MultibandTile => MultibandTile] = List(
      maybeEqualize,
      Some(normalizeAndClampValues),
      maybeAdjustBrightness,
      maybeAdjustGamma,
      maybeAdjustContrast,
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
        (1<< ct.bits) - 1
      case _: ByteCells | _: ShortCells | _: IntCells =>
        ((1<<ct.bits) - 1) - (1<<(ct.bits-1))
    }

  def normalizeAndClamp(tile: Tile, oldMin: Int, oldMax: Int, newMin: Int, newMax: Int): Tile = {
    val dNew = newMax - newMin
    val dOld = oldMax - oldMin
    tile.mapIfSet { z =>
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
