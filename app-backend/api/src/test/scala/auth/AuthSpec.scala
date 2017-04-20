package com.azavea.rf.api.auth

import com.azavea.rf.datamodel._
import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.model.StatusCodes
import akka.actor.ActorSystem

import concurrent.duration._
import akka.http.scaladsl.server.Route
import com.azavea.rf.api.user._
import com.azavea.rf.api.utils.Config
import com.azavea.rf.api._
import com.azavea.rf.common._

import io.circe._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

class AuthSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {
  implicit val ec = system.dispatcher
  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(5).second)
  val newUserId = "NewUser"

  val token = AuthUtils.generateToken(newUserId)
  val authorization = AuthUtils.generateAuthHeader(newUserId)

  // Alias to baseRoutes to be explicit
  val baseRoutes = routes

  /** Route for testing the authenticate directive.
    *
    * If authenticate provides a user, a get request to "/" will return a 202 Accepted
    */
  def authenticateDirectiveTestRoute: Route = {
    authenticate { user =>
      get {
        complete(StatusCodes.Accepted)
      }
    }
  }

  def authenticateQueryParameterTestRoute: Route = {
    validateTokenParameter { token =>
      get {
        complete(StatusCodes.Accepted)
      }
    }
  }

  "authenticate directive" should {
    "Reject anonymous users" in {
      Get("/") ~> authenticateDirectiveTestRoute ~> check {
        rejection
      }
    }

    "create a new user then authenticate using it if a user which matches the JWT token doesn't exist" in {
      Get("/").addHeader(authorization) ~> authenticateDirectiveTestRoute ~> check {
        Get(s"/api/users/$newUserId")
          .addHeader(authorization) ~> baseRoutes ~> check {
          responseAs[User]
        }
      }
    }
  }

  "authenticate query parameters" should {
    "Reject no query parameters" in {
      Get("/") ~> authenticateQueryParameterTestRoute ~> check {
        rejection
      }
    }
    "Accept with token query parameters" in {
      Get(s"/?token=${token}") ~> authenticateQueryParameterTestRoute ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }
  }
}
