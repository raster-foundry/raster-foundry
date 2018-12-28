package com.rasterfoundry.backsplash

import java.net.URLDecoder

import com.rasterfoundry.backsplash.color._
import geotrellis.vector.{io => _, _}
import geotrellis.raster.{io => _, _}
import geotrellis.vector.io.readWktOrWkb
import geotrellis.raster.histogram._
import geotrellis.raster.io.geotiff.AutoHigherResolution
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.spark.SpatialKey
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.server.vlm.RasterSourceUtils
import geotrellis.contrib.vlm.geotiff.GeoTiffRasterSource
import cats.data.{NonEmptyList => NEL}
import cats.effect.IO
import io.circe.syntax._
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging

import geotrellis.contrib.vlm.gdal.GDALRasterSource

case class BacksplashImage(imageId: UUID,
                           uri: String,
                           footprint: MultiPolygon,
                           subsetBands: List[Int],
                           corrections: ColorCorrect.Params,
                           singleBandOptions: Option[SingleBandOptions.Params],
                           mtr: MetricsRegistrator)
    extends LazyLogging {

  val getRasterSourceTimer =
    mtr.newTimer(classOf[BacksplashImage], "get-raster-source")

  def read(extent: Extent, cs: CellSize): Option[MultibandTile] = {
    val time = getRasterSourceTimer.time()
    val rs = BacksplashImage.getRasterSource(uri)
    time.stop()
    val destinationExtent = extent.reproject(rs.crs, WebMercator)
    rs.reproject(WebMercator, NearestNeighbor)
      .resampleToGrid(RasterExtent(extent, cs), NearestNeighbor)
      .read(destinationExtent, subsetBands.toSeq)
      .map(_.tile)
  }

  def read(z: Int, x: Int, y: Int): Option[MultibandTile] = {
    val time = getRasterSourceTimer.time()
    val rs = BacksplashImage.getRasterSource(uri)
    time.stop()
    val layoutDefinition = BacksplashImage.tmsLevels(z)
    rs.reproject(WebMercator)
      .tileToLayout(layoutDefinition, NearestNeighbor)
      .read(SpatialKey(x, y), subsetBands) map { tile =>
      tile.mapBands((n: Int, t: Tile) => t.toArrayTile)
    }
  }

  def colorCorrect(z: Int,
                   x: Int,
                   y: Int,
                   hists: Seq[Histogram[Double]],
                   nodataValue: Option[Double]): Option[MultibandTile] =
    read(z, x, y) map {
      corrections.colorCorrect(_, hists, nodataValue)
    }

  def colorCorrect(extent: Extent,
                   cs: CellSize,
                   hists: Seq[Histogram[Double]],
                   nodataValue: Option[Double]) =
    read(extent, cs) map {
      corrections.colorCorrect(_, hists, nodataValue)
    }
}

import scala.collection.mutable.HashMap

object BacksplashImage extends RasterSourceUtils with LazyLogging {

  def getRasterSource(uri: String): GeoTiffRasterSource =
    new GeoTiffRasterSource(uri)
}
