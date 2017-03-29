package com.azavea.rf.tile

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder
import com.typesafe.config.ConfigFactory
import geotrellis.proj4._
import geotrellis.spark._
import geotrellis.spark.tiling._
import geotrellis.vector._

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.Random
import java.util.UUID

object TmsUtils {
  val pyramid = {
    val scheme = ZoomedLayoutScheme(WebMercator)
    for (z <- 1 to 30) yield {
      z -> scheme.levelForZoom(z)
    }
  }.toMap

  /** Given lat, lon, and zoom, generate a canonical (z, x, y) TMS index */
  def getTileZXY(lat: Double, lon: Double, zoom: Int) = {
    val p = Point(lon, lat).reproject(LatLng, WebMercator)
    val SpatialKey(x, y) = pyramid(zoom).layout.mapTransform(p)
    (zoom, x, y)
  }

  /** For a given lat, long, and zoom, generate a full screen (up to 16 tile
    *  requests) of TMS tile indices.
    */
  def tileIdxsForScreen(lat: Double, lon: Double, zoom: Int): Seq[(Int, Int, Int)] = {
    val zoomRes = 1 << zoom // one dimensional resolution
    def wrapCoord(latlon: Int) = latlon % zoomRes

    val (_, x, y) = getTileZXY(lat, lon, zoom)

    /** The logic here is that:
      *  zoom 0: offsetting by 0 makes sense (0, 0)
      *  zoom 1: offsetting by up to 1 makes sense (0-1, 0-1) i.e. a 'space' of 4 tiles
      *  zoom 2: offsetting by up to 2 makes sense (0-2, 0-2) i.e. 9 tiles
      *  zoom 3+: offsetting by up to 3 makes sense (0-3, 0-3) i.e. 16 tiles
      *  Multiple TMS requests are sent out simultaneously for any given centerpoint. The actual
      *   number of such requests will depend on the client, but the assumptions here are
      *   reasonable.
      */
    val requiredColRows = Math.min(zoom, 3)

    for {
      xOffset <- 0 to requiredColRows
      yOffset <- 0 to requiredColRows
    } yield (zoom, wrapCoord(x + xOffset), wrapCoord(y + yOffset))
  }

  // Functions for random values
  val rnd = new Random(Config.TMS.randomSeed)
  def randomLat(min: Double = -90, max: Double = 90) = {
    val minVal = Math.max(min, -90)
    val maxVal = Math.max(minVal, Math.min(max, 90))
    val range = maxVal - minVal
    (rnd.nextDouble() * range) + minVal
  }

  def randomLon(min: Double = -180, max: Double = 180) = {
    val minVal = Math.max(min, -180)
    val maxVal = Math.max(minVal, Math.min(max, 180))
    val range = maxVal - minVal
    (rnd.nextDouble() * range) + minVal
  }

  def randomZoom(min: Int = 1, max: Int = 20) = {
    val minVal = Math.max(min, 1)
    val maxVal = Math.max(minVal, Math.min(20, max))
    val range = maxVal - minVal

    if (range > 0) rnd.nextInt(range) + minVal
    else minVal
  }

  /** Random TMS indices constrained by a provided bounding box and bounding zoom levels */
  def randomTileIdxsForBBox(bbox: Extent = LatLng.worldExtent, minZoom: Int = 1, maxZoom: Int = 20): Seq[(Int, Int, Int)] =
    tileIdxsForScreen(randomLat(bbox.ymin, bbox.ymax), randomLon(bbox.xmin, bbox.xmax), randomZoom(minZoom, maxZoom))

  /** A gatling [[Feeder] instance for generating requests that mimic TMS requests */
  def randomTileFeeder(bbox: Extent = LatLng.worldExtent, minZoom: Int = 1, maxZoom: Int = 20) = {
    Iterator.continually {
      Map("tiles" -> randomTileIdxsForBBox(bbox, minZoom, maxZoom))
    }
  }
}
