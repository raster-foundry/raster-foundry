package com.rasterfoundry.backsplash.color

import com.rasterfoundry.backsplash.error._

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

  def colorTile(tile: MultibandTile,
                histogram: List[Histogram[Double]],
                options: SingleBandOptions.Params): MultibandTile = {
    val singleBandTile = tile.band(0)
    val hist: Histogram[Double] = histogram match {
      case Nil =>
        throw MetadataException(
          "Received an empty list of histograms for single band color correction")
      case hist +: Nil => hist
      case _ =>
        throw MetadataException(
          "Received too many histograms for single band color correction")
    }
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

    val renderedTile =
      cmap.render(singleBandTile.interpretAs(IntUserDefinedNoDataCellType(0)))
    val r = renderedTile.map(_.red).interpretAs(UByteCellType)
    val g = renderedTile.map(_.green).interpretAs(UByteCellType)
    val b = renderedTile.map(_.blue).interpretAs(UByteCellType)
    val a = renderedTile.map(_.alpha).interpretAs(UByteCellType)
    MultibandTile(r, g, b, a)
  }
}
