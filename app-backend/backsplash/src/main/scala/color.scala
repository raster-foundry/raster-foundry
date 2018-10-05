package com.rf.azavea.backsplash
import java.util.UUID

import cats.data.{OptionT, NonEmptyList => NEL}
import cats.effect.{IO, Timer}
import cats.implicits._
import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}
import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.database._
import com.azavea.rf.datamodel.{ColorRampMosaic, MosaicDefinition, SceneType, SingleBandOptions}
import doobie.implicits._
import geotrellis.proj4.{CRS, Proj4Transform, WebMercator}
import geotrellis.raster.histogram._
import geotrellis.raster.io.geotiff.{Auto, AutoHigherResolution}
import geotrellis.raster.io.json.HistogramJsonFormats
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.raster.{CellSize, Raster, io => _, _}
import geotrellis.server.core.cog.CogUtils
import geotrellis.server.core.cog.CogUtils.{closestTiffOverview, cropGeoTiff, tmsLevels}
import geotrellis.server.core.maml.metadata._
import geotrellis.server.core.maml.reification._
import geotrellis.spark.io._
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.spark.io.s3.S3ValueReader
import geotrellis.spark.tiling.{LayoutDefinition, ZoomedLayoutScheme}
import geotrellis.spark.{io => _, _}
import geotrellis.vector.{Extent, Projected}
import io.circe.generic.semiauto._
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global

object color extends RollbarNotifier {
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
      case (None, Some(o), masked@(h +: t)) =>
        ColorRampMosaic.colorMapFromMap(o.toMap map {
          case (k, v) =>
            (k, if (masked.contains(k.toInt)) "#00000000" else v.noSpaces)
        })
      case _ => {
        val message =
          "Invalid color scheme format. Color schemes must be defined as an array of hex colors or a mapping of raster values to hex colors."
        logger.error(message)
        throw new IllegalArgumentException(message)
      }
    }
    Raster(tile.color(colorMap), extent)
  }


}
