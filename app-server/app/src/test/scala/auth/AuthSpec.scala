package com.azavea.rf.auth

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.http.scaladsl.model.StatusCodes
import akka.actor.ActorSystem
import concurrent.duration._
import akka.http.scaladsl.server.Route

import com.azavea.rf.utils.Config
import com.azavea.rf.{DBSpec, Router, AuthUtils}
import com.azavea.rf.user.UserWithOrgs

class AuthSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {
  implicit val ec = system.dispatcher
  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(20).second)
  val newUserId = "NewUser"
  val authorization = AuthUtils.generateAuthHeader(newUserId)

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

  "authenticate directive" should {
    "Reject anonymous users" in {
      Get("/") ~> authenticateDirectiveTestRoute ~> check {
        rejection
      }
    }
    "create a new user then authenticate using it if a user which matches the JWT token doesn't exist" in {
      Get("/").addHeader(authorization) ~> authenticateDirectiveTestRoute ~> check {
        Get(s"/api/users/$newUserId")
          .addHeader(authorization) ~> userRoutes ~> check {
          responseAs[UserWithOrgs]
        }
      }
    }
  }

  /** Route for testing the authenticateAndAllowAnon directive.
    *
    * If authenticate provides a user, a get request to "/" will return a 202 Accepted
    */
  def authenticateAndAllowAnonTestRoute: Route = {
    authenticateAndAllowAnonymous { user =>
      get {
        complete(StatusCodes.Accepted)
      }
    }
  }

  "authenticateAndAllowAnonymous" should {
    "allow anonymous users access to an endpoint" in {
      Get("/") ~> authenticateAndAllowAnonTestRoute ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }
  }
}
