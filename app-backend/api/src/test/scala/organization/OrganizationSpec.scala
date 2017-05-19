package com.azavea.rf.api.organization

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.http.scaladsl.server.Route
import akka.actor.ActorSystem
import concurrent.duration._

import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.Config
import com.azavea.rf.api.Router
import com.azavea.rf.common._
import com.azavea.rf.api.AuthUtils

import io.circe._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

class OrganizationSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {
  implicit val ec = system.dispatcher
  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(5).second)

  // Alias to baseRoutes to be explicit
  val baseRoutes = routes

  val authHeader = AuthUtils.generateAuthHeader("Default")

  "/api/organizations" should {
    "require authentication" in {
      Get("/api/organizations") ~> baseRoutes ~> check {
        rejection
      }
    }

    "return a paginated list of organizations" in {
      Get("/api/organizations")
        .addHeader(authHeader) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Organization]]
      }
    }
    "require authorization for creation of new organizations" in {
      val newOrg = Organization.Create("Test Organization")
      Post("/api/organizations")
        .withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newOrg.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        rejection
      }
    }
  }

  "/api/organizations/{uuid}" should {
    "return an organization" in {
      Get("/api/organizations")
        .addHeader(authHeader) ~> baseRoutes ~> check {
        val orgs = responseAs[PaginatedResponse[Organization]]
        val orgId = orgs.results.head.id

        Get(s"/api/organizations/$orgId")
          .addHeader(authHeader) ~> baseRoutes ~> check {
          responseAs[Organization]
        }
      }
    }

    "return a 404 for non-existent organizations" in {
      val orgUUID = java.util.UUID.randomUUID()
      Get(s"/api/organizations/$orgUUID")
        .addHeader(authHeader) ~> Route.seal(baseRoutes) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
  }
}
