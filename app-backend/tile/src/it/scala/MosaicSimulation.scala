package com.azavea.rf.tile

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.Http

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.Random
import java.util.UUID

/**
* Refresh tokens - get them.
*
* CONFIG:
*  host
*  ports
*  TMS template
*  seed value
*  bounding box
* MISC:
*  Auth headers
*/
class MosaicSimulation extends Simulation with Config {
  import TmsUtils._
  val httpConf = http
    .baseURL("http://computer-database.gatling.io") // Here is the root for all relative URLs
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8") // Here are the common headers
    .doNotTrackHeader("1")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .acceptEncodingHeader("gzip, deflate")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")

  // TODO: something less stupid here
  val projectId: UUID = UUID.randomUUID()

  val singleRandomTile = exec(randomTile())

  val singleRandomScreen = {
    val requests = randomScreen()
    var execution = exec(requests.head)
    requests.tail.foreach { req =>
      execution = execution.exec(req)
    }
    execution
  }

  val tmsScenario =
    scenario("Mosaic TMS")
      .exec(singleRandomScreen)
      .pause(5)

  setUp(
    tmsScenario.inject(rampUsers(10) over (10 seconds)).protocols(httpConf)
  )
}

