package com.rasterfoundry.backsplash

import geotrellis.vector.{Polygon, Extent}
import geotrellis.vector.io.readWktOrWkb
import geotrellis.raster.MultibandTile
import geotrellis.server.vlm.RasterSourceUtils
import geotrellis.contrib.vlm._
import geotrellis.contrib.vlm.gdal._



case class BacksplashImage(uri: String, footprint: Polygon, subsetBands: List[Int]) {
  def read(extent: Extent): Option[MultibandTile] = ???

  def read(z: Int, x: Int, y: Int): Option[MultibandTile] = ???
}

object BacksplashImage extends RasterSourceUtils {
  def apply(uri: String, wkt: String, subsetBands: List[Int]): BacksplashImage =
    readWktOrWkb(wkt).as[Polygon].map { poly =>
      BacksplashImage(uri, poly, subsetBands)
    }.getOrElse(throw new Exception("Hey, this needs to be a polygon, guy/gal"))

  def getRasterSource(uri: String): RasterSource = GDALRasterSource(uri)

  def getRasterExtents(uri: String): cats.effect.IO[cats.data.NonEmptyList[geotrellis.raster.RasterExtent]] = ???

}
