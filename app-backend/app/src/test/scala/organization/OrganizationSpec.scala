package com.azavea.rf.organization

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.http.scaladsl.server.Route
import akka.actor.ActorSystem
import concurrent.duration._
import spray.json._

import com.azavea.rf.datamodel._
import com.azavea.rf.utils.Config
import com.azavea.rf.{DBSpec, Router}
import com.azavea.rf.AuthUtils


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
    "allow creation of new organizations" in {
      val newOrg = Organization.Create("Test Organization")
      Post("/api/organizations")
        .withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newOrg.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        responseAs[Organization]
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
  "/api/organizations/{uuid}/users" should {
    "return a list of user roles for the organization" in {
      Get("/api/organizations")
        .addHeader(authHeader) ~> baseRoutes ~> check {
        val orgs = responseAs[PaginatedResponse[Organization]]
        val orgId = orgs.results.head.id
        Get(s"/api/organizations/$orgId/users")
          .addHeader(authHeader) ~> baseRoutes ~> check {
          responseAs[PaginatedResponse[User.WithRole]]
        }
      }
    }
    "add a user to an organization" in {
      Get("/api/organizations")
        .addHeader(authHeader) ~> baseRoutes ~> check {
        val orgs = responseAs[PaginatedResponse[Organization]]
        val orgId = orgs.results.head.id
        val newUserWithRole = User.WithRoleCreate("Default", User.Viewer)
        Post(s"/api/organizations/$orgId/users")
          .withHeadersAndEntity(
          List(authHeader),
          HttpEntity(
            ContentTypes.`application/json`,
            newUserWithRole.toJson.toString()
          )
        ) ~> baseRoutes ~> check {
          val createdUser = responseAs[User.WithRole]
          Get(s"/api/organizations/$orgId/users/Default")
            .addHeader(authHeader) ~> baseRoutes ~> check {
            responseAs[User.WithRole] shouldEqual createdUser
          }
        }
      }
    }
  }
  "/api/organizations/{uuid}/users/{userId}" should {
    "return a user's role in the organization" in {
      Get("/api/organizations")
        .addHeader(authHeader) ~> baseRoutes ~> check {
        val orgs = responseAs[PaginatedResponse[Organization]]
        val orgId = orgs.results.head.id
        val newUserWithRole = User.WithRoleCreate("Default", User.Viewer)
        Post(s"/api/organizations/$orgId/users")
          .withHeadersAndEntity(
          List(authHeader),
          HttpEntity(
            ContentTypes.`application/json`,
            newUserWithRole.toJson.toString()
          )
        ) ~> baseRoutes ~> check {
          Get(s"/api/organizations/$orgId/users/Default")
            .addHeader(authHeader) ~> baseRoutes ~> check {
            responseAs[User.WithRole]
          }
        }
      }
    }
    "edit a user's role in the organization" in {
      Get("/api/organizations")
        .addHeader(authHeader) ~> baseRoutes ~> check {
        val orgs = responseAs[PaginatedResponse[Organization]]
        val orgId = orgs.results.head.id
        Get(s"/api/organizations/$orgId/users/Default")
        .addHeader(authHeader) ~> baseRoutes ~> check {
          responseAs[User.WithRole]
        }
      }
    }
    "delete a user's role in the organization" in {
      Get("/api/organizations")
        .addHeader(authHeader) ~> baseRoutes ~> check {
        //TODO
      }
    }
  }
}
