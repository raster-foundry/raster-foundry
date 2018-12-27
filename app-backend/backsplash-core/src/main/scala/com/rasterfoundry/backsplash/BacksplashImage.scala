package com.rasterfoundry.backsplash

import com.rasterfoundry.backsplash.color._
import com.rasterfoundry.backsplash.error._
import geotrellis.vector.{io => _, _}
import geotrellis.raster.{io => _, _}
import geotrellis.spark.tiling.{LayoutDefinition, ZoomedLayoutScheme}
import geotrellis.vector.io.readWktOrWkb
import geotrellis.raster.histogram._
import geotrellis.raster.io.geotiff.AutoHigherResolution
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.spark.SpatialKey
import geotrellis.proj4.{WebMercator, LatLng}

import geotrellis.server.vlm.RasterSourceUtils
import geotrellis.contrib.vlm.geotiff.GeoTiffRasterSource

import cats.data.{NonEmptyList => NEL}
import cats.effect.IO
import cats.implicits._
import io.circe.syntax._

import java.util.UUID

case class BacksplashImage(
    imageId: UUID,
    uri: String,
    footprint: MultiPolygon,
    subsetBands: List[Int],
    corrections: ColorCorrect.Params,
    singleBandOptions: Option[SingleBandOptions.Params]) {
  def read(extent: Extent, cs: CellSize): IO[Option[MultibandTile]] =
    for {
      rs <- BacksplashImage.getRasterSource(uri)
      destinationExtent = extent.reproject(rs.crs, WebMercator)
    } yield {
      rs.reproject(WebMercator, NearestNeighbor)
        .resampleToGrid(RasterExtent(extent, cs), NearestNeighbor)
        .read(destinationExtent, subsetBands.toSeq)
        .map(_.tile)
    }

  def read(z: Int, x: Int, y: Int): IO[Option[MultibandTile]] =
    for {
      rs <- BacksplashImage.getRasterSource(uri)
      layoutDefinition = BacksplashImage.tmsLevels(z)
    } yield {
      rs.reproject(WebMercator)
        .tileToLayout(layoutDefinition, NearestNeighbor)
        .read(SpatialKey(x, y), subsetBands)
    }

  def colorCorrect(extent: Extent,
                   cs: CellSize,
                   hists: Seq[Histogram[Double]],
                   nodataValue: Option[Double]) =
    for {
      maybeTile <- read(extent, cs)
    } yield {
      maybeTile map { corrections.colorCorrect(_, hists, nodataValue) }
    }
}

object BacksplashImage {

  val tmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  def getRasterSource(uri: String): IO[GeoTiffRasterSource] = IO {
    new GeoTiffRasterSource(uri)
  }

  def fromWkt(imageId: UUID,
              uri: String,
              wkt: String,
              subsetBands: List[Int]): BacksplashImage =
    readWktOrWkb(wkt)
      .as[Polygon]
      .map { poly =>
        BacksplashImage(imageId,
                        uri,
                        MultiPolygon(poly),
                        subsetBands,
                        ColorCorrect.paramsFromBandSpecOnly(0, 1, 2),
                        None)
      }
      .getOrElse(throw new Exception("Provided WKT/WKB must be a multipolygon"))

  def getRasterExtents(uri: String): IO[NEL[RasterExtent]] =
    for {
      rs <- getRasterSource(uri)
    } yield {
      rs.resolutions.toNel getOrElse {
        throw new MetadataException(s"No overviews available at $uri")
      }
    }
}
