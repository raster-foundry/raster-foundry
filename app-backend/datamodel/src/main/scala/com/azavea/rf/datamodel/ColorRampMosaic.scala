package com.azavea.rf.datamodel

import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.render.{ColorMap, ColorRamp, _}

object ColorRampMosaic extends LazyLogging {

  def parseColor(color: String): Int = {
    Integer.parseUnsignedInt(
      color
        .replaceFirst("#", "")
        .replaceAll("\"", "")
        .reverse
        .padTo(6, "0")
        .reverse
        .padTo(8, "f")
        .mkString,
      16
    )
  }

  @SuppressWarnings(Array("CatchThrowable", "TraversableLast"))
  def colorMapFromMap(colorMap: Map[String, String]): ColorMap = {
    var fallbackColor: Option[Int] = None
    var noDataColor: Option[Int] = None
    val (breaks, colors) = colorMap.toVector
      .flatMap {
        case (break, color) =>
          try {
            break match {
              case "NODATA" =>
                noDataColor = Some(parseColor(color))
                None
              case "FALLBACK" =>
                fallbackColor = Some(parseColor(color))
                None
              case _ =>
                Some((break.toDouble, parseColor(color)))
            }
          } catch {
            case e: Throwable =>
              val message = "Error parsing color map"
              logger.error(message)
              throw new IllegalArgumentException(message).initCause(e)
          }
      }
      .sortWith({ case ((b1: Double, _), (b2: Double, _)) => b1 < b2 })
      .unzip

    val colorRamp = ColorRamp(colors)
    ColorMap(breaks, colorRamp)
      .withNoDataColor(noDataColor.getOrElse(0))
      .withFallbackColor(fallbackColor match {
        case Some(color) =>
          color
        case _ => colors.last
      })
  }

  @SuppressWarnings(Array("CatchThrowable", "TraversableLast"))
  def colorMapFromVector(colors: Vector[String],
                         options: SingleBandOptions.Params,
                         hist: Histogram[Double]): ColorMap = {
    val cleanedColors = colors map { color =>
      try {
        parseColor(color)
      } catch {
        case e: Throwable =>
          val message =
            s"Color in color scheme could not be parsed: $color ; ${e.getLocalizedMessage}"
          logger.error(message)
          throw new IllegalArgumentException(message)
      }
    }
    val lastColor = cleanedColors.last
    val colorRamp = ColorRamp(cleanedColors)

    // TODO allow setting min / max through options
    // this would also include optionally masking values outside the specified range
    val (min, max) = hist.minMaxValues match {
      case Some((min, max)) =>
        (min, max)
      case _ =>
        val message = "Unable to get histogram min / max"
        logger.error(message)
        throw new IllegalArgumentException(message)
    }

    val steps = options.colorBins match {
      case 0    => 255
      case bins => bins
    }

    val step = (max - min) / steps
    val breaks = for (j <- 0 until steps) yield min + j * step

    // as an optimization, we could cache the color maps, but I'm not sure it's worth it
    ColorMap(breaks.toArray, colorRamp.stops(steps))
      .withFallbackColor(lastColor)
  }

  def apply(tiles: List[(MultibandTile, Array[Histogram[Double]])],
            options: SingleBandOptions.Params): Option[MultibandTile] = {
    val band: Int = options.band
    val singleBandTiles = tiles map {
      case (tile: MultibandTile, _) =>
        tile.band(band)
    }

    if (singleBandTiles.isEmpty) {
      None
    } else {
      val hist: Histogram[Double] = tiles
        .map {
          case (_, histograms: Array[Histogram[Double]]) =>
            histograms.head
        }
        .reduce(_ merge _)
      val cmap =
        (options.colorScheme.asArray, options.colorScheme.asObject) match {
          case (Some(a), None) =>
            colorMapFromVector(a.map(e => e.noSpaces), options, hist)
          case (None, Some(o)) =>
            colorMapFromMap(o.toMap map { case (k, v) => (k, v.noSpaces) })
          case _ =>
            val message =
              "Invalid color scheme format. Color schemes must be defined as an array of hex colors or a mapping of raster values to hex colors."
            logger.error(message)
            throw new IllegalArgumentException(message)
        }
      val tile = cmap.render(singleBandTiles.reduce(_ merge _))
      val r = tile.map(_.red).interpretAs(UByteCellType)
      val g = tile.map(_.green).interpretAs(UByteCellType)
      val b = tile.map(_.blue).interpretAs(UByteCellType)
      val a = tile.map(_.alpha).interpretAs(UByteCellType)
      Some(MultibandTile(r, g, b, a))
    }
  }
}
