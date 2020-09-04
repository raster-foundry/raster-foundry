package com.rasterfoundry.backsplash

import com.rasterfoundry.common.BacksplashGeoTiffInfo
import com.rasterfoundry.common.color._
import com.rasterfoundry.database.SceneDao
import com.rasterfoundry.database.util.{Cache => DBCache}
import com.rasterfoundry.datamodel.{SceneMetadataFields, SingleBandOptions}

import cats.effect.IO
import cats.implicits._
import com.colisweb.tracing.core.TracingContext
import com.typesafe.scalalogging.LazyLogging
import doobie.implicits._
import doobie.util.transactor.Transactor
import geotrellis.layer.Implicits._
import geotrellis.layer.{SpatialKey, ZoomedLayoutScheme}
import geotrellis.proj4.WebMercator
import geotrellis.raster.gdal.GDALRasterSource
import geotrellis.raster.geotiff.{GeoTiffPath, GeoTiffRasterSource}
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.raster.{MultibandTile, RasterSource}
import geotrellis.raster.{io => _, _}
import geotrellis.vector.MultiPolygon
import geotrellis.vector.{io => _, _}
import scalacache.CatsEffect.modes._
import scalacache._

import scala.concurrent.duration._

import java.net.URLDecoder
import java.util.UUID

/** An image used in a tile or export service, can be color corrected, and requested a subet of the bands from the
  * image
  *
  * @param imageId UUID of the image (scene) in the database
  * @param projectLayerId UUID of the layer this image is a part of
  * @param uri location of the source data
  * @param subsetBands subset of bands to be read from source
  * @param corrections description + operations for how to correct image
  * @param singleBandOptions band + options of how to color a single band
  * @param mask geometry to limit the rendering
  */
final case class BacksplashGeotiff(
    imageId: UUID,
    projectId: UUID,
    projectLayerId: UUID,
    uri: String,
    subsetBands: List[Int],
    corrections: ColorCorrect.Params,
    singleBandOptions: Option[SingleBandOptions.Params],
    mask: Option[MultiPolygon],
    footprint: MultiPolygon,
    metadata: SceneMetadataFields,
    disableAutoCorrect: Option[Boolean],
    xa: Transactor[IO]
) extends LazyLogging
    with BacksplashImage[IO] {

  import DBCache.GeotiffInfoCache._

  val tags: Map[String, String] = Map(
    "imageId" -> imageId.toString,
    "projectId" -> projectId.toString,
    "projectLayerId" -> projectLayerId.toString,
    "uri" -> uri
  )

  def getRasterSource(context: TracingContext[IO]): IO[RasterSource] = {
    context.span("getRasterSource", tags) use { child =>
      if (enableGDAL) {
        logger.debug(s"Using GDAL Raster Source: ${uri}")
        val rasterSource = GDALRasterSource(URLDecoder.decode(uri, "UTF-8"))
        IO {
          metadata.noDataValue match {
            case Some(nd) =>
              rasterSource.interpretAs(DoubleUserDefinedNoDataCellType(nd))
            case _ =>
              rasterSource
          }
        }
      } else {
        for {
          infoOption <- child.span("getSceneGeoTiffInfo") use { _ =>
            SceneDao.getSceneGeoTiffInfo(imageId).transact(xa)
          }
          info <- child.span("getGeotiffInfoFromSource") use { _ =>
            infoOption match {
              case Some(i) => IO.pure(i)
              case _ => {
                for {
                  sourceInfo <- IO(BacksplashGeotiffReader.getGeotiffInfo(uri))
                  _ <- SceneDao
                    .updateSceneGeoTiffInfo(sourceInfo, imageId)
                    .transact(xa)
                  _ <- put[IO, BacksplashGeoTiffInfo](s"SceneInfo:$imageId")(
                    sourceInfo,
                    Some(30 minutes)
                  )
                } yield sourceInfo
              }
            }
          }
          reader <- child.span("getByteReader") use { _ =>
            IO(getByteReader(uri))
          }
          gtInfo <- child.span("toGeotiffInfo") use { _ =>
            IO(info.toGeotiffInfo(reader))
          }
        } yield {
          GeoTiffRasterSource(
            GeoTiffPath(uri),
            baseTiff =
              Some(BacksplashGeotiffReader.readMultibandWithInfo(gtInfo))
          )
        }
      }
    }
  }

  def read(
      z: Int,
      x: Int,
      y: Int,
      context: TracingContext[IO]
  ): IO[Option[MultibandTile]] = {
    val readTags = tags.combine(Map("zoom" -> z.toString))
    context.span("read:z_x_y:", readTags) use { childContext =>
      val layoutDefinition = BacksplashImage.tmsLevels(z)
      val cellArea = BacksplashImage.getWebMercatorPixelArea(z)
      val requiredPixelRatio = 3.9

      // If the data footprint isn't big enough to fill two pixels, just
      // don't even bother
      logger.debug(
        s"Area: ${footprint.getArea}. Required: ${requiredPixelRatio * cellArea}"
      )

      if (footprint.getArea < requiredPixelRatio * cellArea) {
        childContext.span("rasterSource.invisiTile") use { _ =>
          IO.pure(Option.empty)
        }
      } else {
        for {
          rasterSource <- getRasterSource(childContext)
          tile <- childContext.span("rasterSource.read") use { _ =>
            IO(
              rasterSource
                .reproject(WebMercator, method = NearestNeighbor)
                .tileToLayout(layoutDefinition)
                .read(SpatialKey(x, y), subsetBands)
            ).map(_.map { tile =>
                tile.mapBands((_: Int, t: Tile) => t.toArrayTile)
              })
              .attempt
          }
        } yield {
          tile match {
            case Left(e)              => throw e
            case Right(multiBandTile) => multiBandTile
          }
        }
      }
    }
  }

  def read(
      extent: Extent,
      cs: CellSize,
      context: TracingContext[IO]
  ): IO[Option[MultibandTile]] = {
    val readTags =
      tags.combine(Map("extent" -> extent.toString, "cellSize" -> cs.toString))
    context.span("read:extent_cs:", readTags) use { child =>
      val rasterExtent = RasterExtent(extent, cs)
      logger.debug(
        s"Expecting to read ${rasterExtent.cols * rasterExtent.rows} cells (${rasterExtent.cols} cols, ${rasterExtent.rows} rows)"
      )
      for {
        rasterSource <- getRasterSource(child)
        tile <- child.span("rasterSource.read:extent_cs:", readTags) use { _ =>
          IO(
            rasterSource
              .reproject(WebMercator, method = NearestNeighbor)
              .resampleToGrid(
                GridExtent[Long](
                  rasterExtent.extent,
                  rasterExtent.cellSize
                ),
                NearestNeighbor
              )
              .read(extent, subsetBands)
              .map(_.tile)
          ).attempt
            .map {
              case Left(e)              => throw e
              case Right(multibandTile) => multibandTile
            }
        }
      } yield {
        tile
      }
    }
  }

  def selectBands(bands: List[Int]): BacksplashGeotiff =
    this.copy(subsetBands = bands)
}

sealed trait BacksplashImage[F[_]] extends LazyLogging {

  val footprint: MultiPolygon
  val imageId: UUID
  val subsetBands: List[Int]
  val corrections: ColorCorrect.Params
  val singleBandOptions: Option[SingleBandOptions.Params]
  val projectId: UUID
  val projectLayerId: UUID
  val mask: Option[MultiPolygon]
  val metadata: SceneMetadataFields
  val disableAutoCorrect: Option[Boolean]

  val enableGDAL = Config.RasterSource.enableGDAL

  /** Read ZXY tile with tracing **/
  def read(
      z: Int,
      x: Int,
      y: Int,
      context: TracingContext[F]
  ): F[Option[MultibandTile]]

  /** Read tile with tracing **/
  def read(
      extent: Extent,
      cs: CellSize,
      context: TracingContext[F]
  ): F[Option[MultibandTile]]

  def getRasterSource(context: TracingContext[F]): F[RasterSource]

  def selectBands(bands: List[Int]): BacksplashImage[F]

  def tagRasterSource(rs: RasterSource): Map[String, String] = {
    Map("rasterSourcePath" -> s"${rs.metadata.name}")
  }
}

object BacksplashImage {
  val tmsLevels = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  def getWebMercatorPixelArea(zoom: Int): Double = {
    val level = tmsLevels(zoom)
    (level.cellwidth * level.cellheight)
  }

}
