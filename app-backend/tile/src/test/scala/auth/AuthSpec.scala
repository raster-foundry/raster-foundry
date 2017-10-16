package com.azavea.rf.tile

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}

class AuthSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest {

  val router = new Router()

  "tile authentication" should {
    "reject anonymous users" in {
      Get("/tools/a89ae9bb-47e5-469c-8329-9c491a1011ae") ~> router.root ~> check {
        rejection
      }
    }
  }

  "tile authentication" should {
    "reject invalid tokens" in {
      Get("/tools/a89ae9bb-47e5-469c-8329-9c491a1011ae/?token=not-valid") ~> router.root ~> check {
        rejection
      }
    }
  }
}
