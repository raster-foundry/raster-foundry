package com.azavea.rf.tile

import com.azavea.rf.tool._

import geotrellis.raster.Tile
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.render.png._
import geotrellis.raster.histogram.Histogram

import scala.math.abs
import java.util.Arrays.binarySearch

package object image {
  implicit class renderTileWithDefinition(tile: Tile) {

    /** This function produces a function from cell value to color appropriate to the color
      *  space defined by the provided [[RenderDefinition]]
      */
    private def buildFn(definition: RenderDefinition): Double => RGBA =
      definition.scale match {
        case Continuous | Sequential | Diverging => continuous(definition)
        case Qualitative(fallback)               => qual(definition, fallback)
      }

    /** RGB color interpolation logic */
    private def RgbLerp(color1: RGBA, color2: RGBA, proportion: Double): Int = {
      val r = (color1.red + (color2.red - color1.red) * proportion).toInt
      val g = (color1.green + (color2.green - color1.green) * proportion).toInt
      val b = (color1.blue + (color2.blue - color1.blue) * proportion).toInt
      val a
        : Double = (color1.alpha + (color2.alpha - color1.alpha) * proportion).toDouble / 2.55
      RGBA(r, g, b, a)
    }

    /** For production of colors along a continuum */
    private def continuous(definition: RenderDefinition): Double => RGBA = {
      dbl: Double =>
        val decomposed = definition.breakpoints.toArray.sortBy(_._1).unzip
        val breaks: Array[Double] = decomposed._1
        val colors: Array[RGBA] = decomposed._2

        val insertionPoint: Int = binarySearch(breaks, dbl)
        if (insertionPoint == -1) {
          // MIN VALUE
          definition.clip match {
            case ClipNone | ClipRight => colors(0)
            case ClipLeft | ClipBoth  => 0x00000000
          }
        } else if (abs(insertionPoint) - 1 == breaks.size) {
          // MAX VALUE
          definition.clip match {
            case ClipNone | ClipLeft  => colors.last
            case ClipRight | ClipBoth => 0x00000000
          }
        } else if (insertionPoint < 0) {
          // MUST INTERPOLATE
          val lowerIdx = abs(insertionPoint) - 2
          val higherIdx = abs(insertionPoint) - 1
          val lower = breaks(lowerIdx)
          val higher = breaks(higherIdx)
          val proportion = (dbl - lower) / (higher - lower)

          RgbLerp(colors(lowerIdx), colors(higherIdx), proportion)
        } else {
          // Direct hit
          colors(insertionPoint)
        }
    }

    /** For production of colors according to discrete breaks */
    private def qual(definition: RenderDefinition,
                     fallback: RGBA): Double => RGBA = { dbl: Double =>
      val decomposed = definition.breakpoints.toArray.sortBy(_._1).unzip
      val breaks: Array[Double] = decomposed._1
      val colors: Array[RGBA] = decomposed._2

      val insertionPoint: Int = binarySearch(breaks, dbl)
      if (insertionPoint == -1) {
        // MIN VALUE
        definition.clip match {
          case ClipNone | ClipRight => fallback
          case ClipLeft | ClipBoth  => 0x00000000
        }
      } else if (abs(insertionPoint) - 1 == breaks.size) {
        // MAX VALUE
        definition.clip match {
          case ClipNone | ClipLeft  => fallback
          case ClipRight | ClipBoth => 0x00000000
        }
      } else if (insertionPoint < 0) {
        // GRAB LOWER VALUE
        colors(abs(insertionPoint) - 2)
      } else {
        // Direct hit
        if (insertionPoint == colors.size - 1) colors(insertionPoint - 1)
        else colors(insertionPoint)
      }
    }

    /** This method extension provides sugar to match GeoTrellis' renderPng method */
    def renderPng(definition: RenderDefinition): Png = {
      val toWrite =
        if (tile.cellType.isFloatingPoint) {
          tile.mapDouble({ cellValue: Double =>
            buildFn(definition)(cellValue).int
          })
        } else {
          tile.map({ cellValue: Int =>
            buildFn(definition)(cellValue).int
          })
        }

      new PngEncoder(Settings(RgbaPngEncoding, PaethFilter))
        .writeByteArray(toWrite)
    }
  }
}
