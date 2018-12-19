package rfgatling

import _root_.io.gatling.http.Predef._
import _root_.io.gatling.core.Predef._
import geotrellis.spark.tiling._
import geotrellis.proj4._

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.Random
import java.util.UUID

class MosaicTmsSimulation extends Simulation {

  val httpConf = http
    .baseUrl(Config.RF.apiHost) // Here is the root for all relative URLs
    .acceptHeader(
      "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8") // Here are the common headers
    .doNotTrackHeader("1")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .acceptEncodingHeader("gzip, deflate")
    .userAgentHeader(
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")

  val bbox = {
    val uuid = UUID.fromString(Config.RF.projectId)
    ApiUtils.getProjectBBox(uuid).getOrElse(LatLng.worldExtent)
  }

  val tmsScenario =
    scenario("Mosaic TMS")
      .exec(feed(TmsUtils
        .randomTileFeeder(bbox, Config.TMS.minZoom, Config.TMS.maxZoom)))
      .exec(_.set("authToken", ApiUtils.getAuthToken))
      .exec(_.set("projectId", Config.RF.projectId))
      .exec(foreach("${tiles}", "tile") {
        exec({ session =>
          val tile = session("tile").as[(Int, Int, Int)]
          session.set("z", tile._1).set("x", tile._2).set("y", tile._3)
        }).exec {
            http("tiles at ${tile._1}/${tile._2}/${tile._3}")
              .get(Config.TMS.template)
              .header("authorization", "Bearer ${authToken}")
              .check(status.is(200))
          }
      })
      .pause(4)

  setUp(
    tmsScenario
      .inject(
        rampUsers(Config.Users.count) during (Config.Users.rampupTime seconds))
      .protocols(httpConf)
  ).assertions(
    global.responseTime.percentile2.lt(1000)
  )
}
