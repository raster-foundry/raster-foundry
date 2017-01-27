package com.azavea.rf.tile

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.model.StatusCodes
import akka.actor.ActorSystem

import concurrent.duration._
import akka.http.scaladsl.server.Route

class AuthSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest {

  val router = new Router()

  "tile authentication" should {
    "reject anonymous users" in {
      Get("/tiles") ~> router.root ~> check {
        rejection
      }
    }
  }

  "tile authentication" should {
    "reject invalid tokens" in {
      Get("/tiles?token=not-valid") ~> router.root ~> check {
        rejection
      }
    }
  }
}
