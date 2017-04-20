package com.azavea.rf.api.user

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.actor.ActorSystem
import concurrent.duration._

import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.Config
import com.azavea.rf.api.Router
import com.azavea.rf.common._
import com.azavea.rf.api.AuthUtils

import io.circe._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

class UserSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {
  implicit val ec = system.dispatcher
  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(5).second)

  val authHeader = AuthUtils.generateAuthHeader("Default")

  // Alias to baseRoutes to be explicit
  val baseRoutes = routes

  "/api/users" should {
    "require authorization for returning a paginated list of users" in {
      Get("/api/users").withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        // responseAs[PaginatedResponse[User]]
        rejection
      }
    }

    "require authentication" in {
      Get("/api/users") ~> baseRoutes ~> check {
        rejection
      }
    }
  }

  "/api/users/{UUID}" should {
    "return a single user" in {
      Get("/api/users/Default")
        .addHeader(authHeader)~> baseRoutes ~> check {
        responseAs[User]
      }
    }
  }
}
