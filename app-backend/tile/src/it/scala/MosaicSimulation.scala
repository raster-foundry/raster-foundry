package com.azavea.rf.tile

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.Http
import geotrellis.spark.tiling._
import geotrellis.proj4._
import geotrellis.vector.Extent

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.Random
import java.util.UUID


class MosaicSimulation extends Simulation {
  val httpConf = http
    .baseURL(Config.RF.host) // Here is the root for all relative URLs
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8") // Here are the common headers
    .doNotTrackHeader("1")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .acceptEncodingHeader("gzip, deflate")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")

  val bbox = {
    val uuid = UUID.fromString(Config.RF.projectId)
    ApiUtils.getProjectBBox(uuid).getOrElse(LatLng.worldExtent)
  }

  val tmsScenario =
    scenario("Mosaic TMS")
      .exec(feed(TmsUtils.randomTileFeeder(bbox, Config.TMS.minZoom, Config.TMS.maxZoom)))
      .exec(_.set("authToken", ApiUtils.getAuthToken))
      .exec(_.set("projectId", Config.RF.projectId))
      .exec(foreach("${tiles}", "tile") {
        exec({session =>
          val tile = session.get("tile").as[(Int, Int, Int)]
          session.set("z", tile._1).set("x", tile._2).set("y", tile._3)
        })
        .exec {
          http("tiles at ${tile._1}/${tile._2}/${tile._3}")
            .get(Config.TMS.template)
            .header("authorization", "Bearer ${authToken}")
            .check(status.is(200))
        }
      }).pause(4)

  setUp(
    tmsScenario.inject(rampUsers(Config.Users.count) over (Config.Users.rampupTime seconds)).protocols(httpConf)
  )
}

