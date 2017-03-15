package com.azavea.rf.tile

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.Random
import java.util.UUID

object TmsUtils {
  val rnd = new Random(42)
  def randomLat() = ((rnd.nextDouble() * 180) - 90).toInt
  def randomLon() = ((rnd.nextDouble() * 360) - 180).toInt
  def randomZoom() = rnd.nextInt(19) + 1

  def getTileY(lat: Double, zoom: Int):  Int = {
    val zoomRes = 1 << zoom // one dimensional resolution
    val yIdx = Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * zoomRes)

    if (yIdx < 0)
      0
    else if (yIdx >= zoomRes)
      zoomRes - 1
    else
      yIdx.toInt
  }

  def getTileX(lon: Double, zoom: Int): Int = {
    val zoomRes = 1 << zoom // one dimensional resolution
    val xIdx = Math.floor((lon + 180) / 360 * zoomRes)

    if (xIdx < 0)
      0
    else if (xIdx >= zoomRes)
      zoomRes - 1
    else
      xIdx.toInt
  }

  def getTileZXY(lat: Double, lon: Double, zoom: Int) =
    (zoom, getTileX(lon, zoom), getTileY(lat, zoom))

  def tileIdxsForScreen(lat: Double, lon: Double, zoom: Int) = {
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

  def mosaicTMS(z: Int, x: Int, y: Int) =
    http(s"project at $z/$x/$y").get(s"project/$z/$x/$y")

  def tileAt(lat: Double, lon: Double, zoom: Int) = {
    val (z, x, y) = getTileZXY(lat, lon, zoom)
    mosaicTMS(z, x, y)
  }

  def tilesForScreenAt(lat: Double, lon: Double, zoom: Int) =
    tileIdxsForScreen(lat, lon, zoom).map { case (z, x, y) =>
      mosaicTMS(z, x, y)
    }

  def randomTile() = tileAt(randomLat(), randomLon(), randomZoom())

  def randomScreen() = tilesForScreenAt(randomLat(), randomLon(), randomZoom())

  val randomTileFeeder = {
    Iterator.continually {
      tilesForScreenAt(randomLat(), randomLon(), randomZoom())
    }
  }
}

