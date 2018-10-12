package com.rf.azavea.backsplash

import cats.data.{NonEmptyList => NEL}
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.datamodel.{ColorRampMosaic, SingleBandOptions}
import geotrellis.raster.histogram._
import geotrellis.raster.{Raster, io => _, _}
import geotrellis.spark.{io => _}
import geotrellis.vector.Extent

object Color extends RollbarNotifier {
  def colorSingleBandTile(
      tile: Tile,
      extent: Extent,
      histogram: Histogram[Double],
      singleBandOptions: SingleBandOptions.Params): Raster[Tile] = {
    logger.debug(s"Applying Colorings")
    val colorScheme = singleBandOptions.colorScheme
    val colorMap = (colorScheme.asArray,
                    colorScheme.asObject,
                    singleBandOptions.extraNoData) match {
      case (Some(a), None, _) =>
        ColorRampMosaic.colorMapFromVector(a.map(_.noSpaces),
                                           singleBandOptions,
                                           histogram)
      case (None, Some(o), Nil) =>
        ColorRampMosaic.colorMapFromMap(o.toMap map {
          case (k, v) => (k, v.noSpaces)
        })
      case (None, Some(o), masked @ (h +: t)) =>
        ColorRampMosaic.colorMapFromMap(o.toMap map {
          case (k, v) =>
            (k, if (masked.contains(k.toInt)) "#00000000" else v.noSpaces)
        })
      case _ => {
        val message =
          "Invalid color scheme format. Color schemes must be defined as an array of hex colors or a mapping of raster values to hex colors."
        logger.error(message)
        throw SingleBandOptionsException(message)
      }
    }
    Raster(tile.color(colorMap), extent)
  }

}
