package com.azavea.rf.organization

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.actor.ActorSystem
import concurrent.duration._
import spray.json._

import com.azavea.rf.utils.Config
import com.azavea.rf.{DBSpec, Router}
import com.azavea.rf.datamodel.latest.schema.tables.OrganizationsRow
import com.azavea.rf.utils.PaginatedResponse
import com.azavea.rf.AuthUtils


class OrganizationSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {
  implicit val ec = system.dispatcher
  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(20).second)

  val authorization = AuthUtils.generateAuthHeader("Default")
  "/api/organizations" should {
    "require authentication" in {
      Get("/api/organizations") ~> organizationRoutes ~> check {
        rejection
      }
    }

    "return a paginated list of organizations" in {
      Get("/api/organizations")
        .addHeader(authorization) ~> organizationRoutes ~> check {
        responseAs[PaginatedResponse[OrganizationsRow]]
      }
    }
    "allow creation of new organizations" in {
      val newOrg = OrganizationsRowCreate("Test Organization")
      Post("/api/organizations")
        .withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newOrg.toJson(organizationsRowCreateFormat).toString()
        )
      ) ~> organizationRoutes ~> check {
        responseAs[OrganizationsRow]
      }
    }
  }

  "/api/organizations/{uuid}" should {
    "return an organization" in {
      Get("/api/organizations")
        .addHeader(authorization) ~> organizationRoutes ~> check {
        val orgs = responseAs[PaginatedResponse[OrganizationsRow]]
        val orgId = orgs.results.head.id

        Get(s"/api/organizations/$orgId")
          .addHeader(authorization) ~> organizationRoutes ~> check {
          responseAs[OrganizationsRow]
        }
      }
    }

    "return a 404 for non-existent organizations" in {
      val orgUUID = java.util.UUID.randomUUID()
      Get(s"/api/organizations/$orgUUID")
        .addHeader(authorization) ~> organizationRoutes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
  }
  "/api/organizations/{uuid}/users" should {
    "return a list of user roles for the organization" in {
      Get("/api/organizations")
        .addHeader(authorization) ~> organizationRoutes ~> check {
        val orgs = responseAs[PaginatedResponse[OrganizationsRow]]
        val orgId = orgs.results.head.id
        Get(s"/api/organizations/$orgId/users")
          .addHeader(authorization) ~> organizationRoutes ~> check {
          responseAs[PaginatedResponse[UserWithRole]]
        }
      }
    }
    "add a user to an organization" in {
      Get("/api/organizations")
        .addHeader(authorization) ~> organizationRoutes ~> check {
        val orgs = responseAs[PaginatedResponse[OrganizationsRow]]
        val orgId = orgs.results.head.id
        val newUserWithRole = UserWithRoleCreate("Default", "viewer")
        Post(s"/api/organizations/$orgId/users")
          .withHeadersAndEntity(
          List(authorization),
          HttpEntity(
            ContentTypes.`application/json`,
            newUserWithRole.toJson(userWithRoleCreateFormat).toString()
          )
        ) ~> organizationRoutes ~> check {
          val createdUser = responseAs[UserWithRole]
          Get(s"/api/organizations/$orgId/users/Default")
            .addHeader(authorization) ~> organizationRoutes ~> check {
            responseAs[UserWithRole] shouldEqual createdUser
          }
        }
      }
    }
  }
  "/api/organizations/{uuid}/users/{userId}" should {
    "return a user's role in the organization" in {
      Get("/api/organizations")
        .addHeader(authorization) ~> organizationRoutes ~> check {
        val orgs = responseAs[PaginatedResponse[OrganizationsRow]]
        val orgId = orgs.results.head.id
        val newUserWithRole = UserWithRoleCreate("Default", "viewer")
        Post(s"/api/organizations/$orgId/users")
          .withHeadersAndEntity(
          List(authorization),
          HttpEntity(
            ContentTypes.`application/json`,
            newUserWithRole.toJson(userWithRoleCreateFormat).toString()
          )
        ) ~> organizationRoutes ~> check {
          Get(s"/api/organizations/$orgId/users/Default")
            .addHeader(authorization) ~> organizationRoutes ~> check {
            responseAs[UserWithRole]
          }
        }
      }
    }
    "edit a user's role in the organization" in {
      Get("/api/organizations")
        .addHeader(authorization) ~> organizationRoutes ~> check {
        val orgs = responseAs[PaginatedResponse[OrganizationsRow]]
        val orgId = orgs.results.head.id
        Get(s"/api/organizations/$orgId/users/Default")
        .addHeader(authorization) ~> organizationRoutes ~> check {
          responseAs[UserWithRole]
        }
      }
    }
    "delete a user's role in the organization" in {
      Get("/api/organizations")
        .addHeader(authorization) ~> organizationRoutes ~> check {
        //TODO
      }
    }
  }
}
