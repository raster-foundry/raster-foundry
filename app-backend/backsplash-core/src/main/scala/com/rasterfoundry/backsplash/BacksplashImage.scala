package com.rasterfoundry.backsplash

import com.rasterfoundry.backsplash.color._
import geotrellis.vector.{io => _, _}
import geotrellis.raster.{io => _, _}
import geotrellis.vector.io.readWktOrWkb
import geotrellis.raster.histogram._
import geotrellis.raster.io.geotiff.AutoHigherResolution
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.spark.SpatialKey
import geotrellis.proj4.{WebMercator, LatLng}

import geotrellis.server.vlm.RasterSourceUtils
import geotrellis.contrib.vlm._
import geotrellis.contrib.vlm.gdal._

import cats.data.{NonEmptyList => NEL}
import cats.effect.IO
import io.circe.syntax._

import java.net.URLDecoder
import java.util.UUID

case class BacksplashImage(
    uri: String,
    footprint: MultiPolygon,
    subsetBands: List[Int],
    corrections: ColorCorrect.Params,
    singleBandOptions: Option[SingleBandOptions.Params]) {
  def read(extent: Extent, cs: CellSize): Option[MultibandTile] = {
    val rs = BacksplashImage.getRasterSource(uri)
    val destinationExtent = extent.reproject(rs.crs, WebMercator)
    rs.reproject(WebMercator)
      .resample(TargetRegion(RasterExtent(destinationExtent, cs)),
                NearestNeighbor,
                AutoHigherResolution)
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

object BacksplashImage extends RasterSourceUtils {

  def getRasterSource(uri: String): GDALRasterSource =
    GDALRasterSource(URLDecoder.decode(uri, "UTF-8"))

  def fromWkt(uri: String,
              wkt: String,
              subsetBands: List[Int]): BacksplashImage =
    readWktOrWkb(wkt)
      .as[Polygon]
      .map { poly =>
        BacksplashImage(uri,
                        MultiPolygon(poly),
                        subsetBands,
                        ColorCorrect.paramsFromBandSpecOnly(0, 1, 2),
                        None)
      }
      .getOrElse(throw new Exception("Provided WKT/WKB must be a multipolygon"))

  def getRasterExtents(uri: String): IO[NEL[RasterExtent]] = {
    val rs = getRasterSource(uri)
    val dataset = rs.dataset
    val band = dataset.getRasterBand(1)

    IO {
      NEL(
        rs.rasterExtent,
        (0 until band.getOverviewCount).toList.map { idx =>
          val ovr = band.getOverview(idx)
          RasterExtent(rs.extent, CellSize(ovr.getXSize, ovr.getYSize))
        }
      )
    }
  }
}
