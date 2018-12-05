package com.rasterfoundry.backsplash

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.vector.io.readWktOrWkb
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.spark.SpatialKey
import geotrellis.proj4.{WebMercator, LatLng}

import geotrellis.server.vlm.RasterSourceUtils
import geotrellis.contrib.vlm._
import geotrellis.contrib.vlm.gdal._

import cats.data.{NonEmptyList => NEL}
import cats.effect.IO


case class BacksplashImage(uri: String, footprint: Polygon, subsetBands: List[Int]) {
  def read(extent: Extent, cs: CellSize): Option[MultibandTile] = {
    val rs = BacksplashImage.getRasterSource(uri)
    val destinationExtent = extent.reproject(LatLng, WebMercator)
    rs.reproject(WebMercator)
      .resample(TargetRegion(RasterExtent(extent, cs)), NearestNeighbor)
      .read(destinationExtent, subsetBands.toSeq)
      .map(_.tile)
  }

  def read(z: Int, x: Int, y: Int): Option[MultibandTile] = {
    val rs = BacksplashImage.getRasterSource(uri)
    val layoutDefinition = BacksplashImage.tmsLevels(z)
    rs.reproject(WebMercator)
      .tileToLayout(layoutDefinition, NearestNeighbor)
      .read(SpatialKey(x, y), subsetBands)
  }
}


object BacksplashImage extends RasterSourceUtils {
  def getRasterSource(uri: String): GDALRasterSource = GDALRasterSource(uri)

  def apply(uri: String, wkt: String, subsetBands: List[Int]): BacksplashImage =
    readWktOrWkb(wkt).as[Polygon].map { poly =>
      BacksplashImage(uri, poly, subsetBands)
    }.getOrElse(throw new Exception("Hey, this needs to be a polygon, guy/gal"))

  def getRasterExtents(uri: String): IO[NEL[RasterExtent]] = {
    val rs = getRasterSource(uri)
    val dataset = rs.dataset
    val band = dataset.GetRasterBand(1)

    IO.pure(NEL(rs.rasterExtent, (0 until band.GetOverviewCount()).toList.map { idx =>
      val ovr = band.GetOverview(idx)
      RasterExtent(rs.extent, CellSize(ovr.GetXSize(), ovr.GetYSize()))
    }))
  }
}
