package rfgatling

import _root_.io.gatling.core.Predef._
import _root_.io.gatling.http.Predef._
import _root_.io.gatling.http.request.builder.HttpRequestBuilder
import geotrellis.proj4._
import geotrellis.spark._
import geotrellis.spark.tiling._
import geotrellis.vector._

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
  def tileIdxsForScreen(lat: Double,
                        lon: Double,
                        zoom: Int): Seq[(Int, Int, Int)] = {
    val zoomRes = 1 << zoom // one dimensional resolution
    def wrapCoord(latlon: Int) = latlon % zoomRes

    val (_, x, y) = getTileZXY(lat, lon, zoom)

    /** The logic here is that a single page load of a map is ~36 requests on a 1080p
      *  screen in chrome at 100 percent zoom - so that's what is being simulated -- a full
      *  map load
      */
    val requiredColRows = 6

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

  def seqToRequest(projectId: UUID,
                   z: Int,
                   x: Int,
                   y: Int,
                   authToken: String): HttpRequestBuilder = {
    http(s"${x}-${y}")
      .get(
        s"/${projectId}/${z}/${x}/${y}/?token=${authToken}"
      )
      .check(status.is(200))
  }

  /** For a given project, extent, and zoom return an [[HttpRequestBuilder]]
    *
    * To better simulate loading a whole zoom level of tiles this constructs the request
    * by making the first tile request and telling gatling that the rest of the tiles are
    * "resources" for the initial tile. This means that the rest of the tiles should be requested
    * in a parallel-ish fashion
    *
    * @param projectId
    * @param bbox
    * @param zoom
    * @param authToken
    * @return
    */
  def randomTileRequestSequence(projectId: UUID,
                                bbox: Extent,
                                zoom: Int,
                                authToken: String): HttpRequestBuilder = {
    val keySeq =
      tileIdxsForScreen(randomLat(bbox.ymin, bbox.ymax),
                        randomLon(bbox.xmin, bbox.xmax),
                        zoom)
    val allRequests = keySeq.map {
      case (z, x, y) => seqToRequest(projectId, z, x, y, authToken)
    }
    allRequests.head.resources(allRequests.tail: _*)
  }

}
