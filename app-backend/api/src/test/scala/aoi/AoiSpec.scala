package com.azavea.rf.api.aois

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import com.azavea.rf.api.{AuthUtils, Router}
import com.azavea.rf.common._
import com.azavea.rf.api.project.ProjectSpecHelper
import com.azavea.rf.api.utils.Config
import com.azavea.rf.datamodel.{AOI, PaginatedResponse, Project}
import geotrellis.slick.Projected
import geotrellis.vector.MultiPolygon
import io.circe.Json
import io.circe.syntax._
import org.scalatest.{Matchers, WordSpec}

// --- //

class AoiSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with ProjectSpecHelper
    with Config
    with Router
    with DBSpec {

  /* Implicit glue to make the compiler happy */
  implicit val ec = system.dispatcher
  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(5).second)

  val baseRoutes = routes
  val authHeader = AuthUtils.generateAuthHeader("Default")

  /* Test data */
  val area: Projected[MultiPolygon] = mpoly.get.copy(srid = 3857)
  val filters: Json = "{}".asJson
  val aoi = AOI.Create(publicOrgId, area, filters, None: Option[String])

  /* Mutable data! Run! */
  var projectId: String = ""
  var aoiId: String = ""

  "/api/aoi/" should {
    "require authentication" in {
      Get("/api/areas-of-interest/") ~> baseRoutes ~> check { reject }

      Get("/api/areas-of-interest/").withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[AOI]].count shouldBe 0
      }
    }
  }

  "/api/projects/{uuid}/areas-of-interest/"  should {
    "create an AOI successfully" in {

      /* Create a Project first, then an AOI associated with it. */

      Post("/api/projects/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newProject1.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        projectId = responseAs[Project].id.toString
      }

      Post(s"/api/projects/${projectId}/areas-of-interest/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(ContentTypes.`application/json`, aoi.asJson.noSpaces)
      ) ~> baseRoutes ~> check {
        aoiId = responseAs[AOI].id.toString
      }
    }

    "read a written AOI" in {
      Get(s"/api/projects/${projectId}/areas-of-interest/").withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[AOI]].count shouldBe 1
      }

      Get(s"/api/areas-of-interest/${aoiId}").withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[AOI]
      }
    }

    "update a written AOI" in {
      Put(s"/api/areas-of-interest/${aoiId}").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(ContentTypes.`application/json`, aoi.asJson.noSpaces)
      ) ~> baseRoutes ~> check {

      }
    }
  }

}
