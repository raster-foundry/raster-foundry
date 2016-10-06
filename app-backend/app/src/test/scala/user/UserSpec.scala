package com.azavea.rf.user

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.actor.ActorSystem
import concurrent.duration._

import com.azavea.rf.utils.Config
import com.azavea.rf.{DBSpec, Router}
import com.azavea.rf.utils.PaginatedResponse
import com.azavea.rf.AuthUtils

class UserSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {
  implicit val ec = system.dispatcher
  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(20).second)

  val authorization = AuthUtils.generateAuthHeader("Default")

  "/api/users" should {
    "return a paginated list of users" in {
      Get("/api/users")
        .addHeader(authorization) ~> userRoutes ~> check {
        responseAs[PaginatedResponse[UserWithOrgs]]
      }
    }

    "require authentication" in {
      Get("/api/users") ~> userRoutes ~> check {
        rejection
      }
    }
  }

  "/api/users/{UUID}" should {
    "return a single user" in {
      Get("/api/users/Default")
        .addHeader(authorization)~> userRoutes ~> check {
        responseAs[UserWithOrgs]
      }
    }
  }
}
