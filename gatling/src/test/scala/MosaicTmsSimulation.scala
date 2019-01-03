package rfgatling

import _root_.io.gatling.http.Predef._
import _root_.io.gatling.core.Predef._
import _root_.io.gatling.core.structure.ChainBuilder
import geotrellis.spark.tiling._
import geotrellis.proj4._

import scala.concurrent.duration._
import java.util.UUID

import geotrellis.vector.Extent

class MosaicTmsSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl(Config.RF.tileHost)
    .doNotTrackHeader("1")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .acceptEncodingHeader("gzip, deflate")
    .acceptHeader(
      "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8") // Here are the common headers
    .userAgentHeader("Gatling RF Mosaic TMS Simulation")

  val projectIds: Array[UUID] = Config.RF.projectIds.split(",") map {
    UUID.fromString
  }
  val bboxes: Map[UUID, Extent] = Map(
    projectIds map { projectId =>
      (projectId,
       ApiUtils.getProjectBBox(projectId).getOrElse(LatLng.worldExtent))
    }: _*
  )

  val authToken: String = ApiUtils.getAuthToken

  /** Chain of HTTP requests grouped by project + zoom level
    *
    * Each user is simulated to load a single zoom level then wait between 1-4
    * seconds before moving on to the next zoom level
    */
  val projectChains: Array[ChainBuilder] = projectIds.flatMap { projectId =>
    (Config.TMS.minZoom until Config.TMS.maxZoom + 1).reverse.map { zoom =>
      val singleZoomLevelMapLoad = TmsUtils.randomTileRequestSequence(
        projectId,
        bboxes.getOrElse(projectIds.head, LatLng.worldExtent),
        zoom,
        authToken)
      group(s"Project: ${projectId}") {
        group(s"Zoom: ${zoom}") {
          exec(singleZoomLevelMapLoad).pause(1 seconds, 4 seconds)
        }
      }
    }
  }

  val tmsScenario =
    scenario("Mosaic TMS").exec(projectChains: _*)

  setUp(
    tmsScenario
      .inject(
        atOnceUsers(1), // Send out initial set of requests for single user, initial ramp-up
        nothingFor(4 seconds), // Wait to start ramping up users
        rampUsers(Config.Users.count) during (Config.Users.rampupTimeSeconds seconds)
      )
      .protocols(httpProtocol)
  ).assertions(
    global.responseTime.percentile3.lt(1000)
  )
}
