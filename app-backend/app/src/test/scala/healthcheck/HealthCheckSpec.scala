package com.azavea.rf.healthcheck

import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import org.scalatest.{Matchers, WordSpec}
import akka.actor.ActorSystem
import concurrent.duration._

import com.azavea.rf.utils.Config
import com.azavea.rf.{DBSpec, Router}


class HealthCheckSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {

  implicit val ec = system.dispatcher
  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(20).second)

  "The healthcheck service" should {
    "return an OK status" in {
      val dbCheck = ServiceCheck("database", HealthCheckStatus.OK)
      val healthCheck = HealthCheck(HealthCheckStatus.OK, Seq(dbCheck))
      val routes = healthCheckRoutes
      Get("/healthcheck") ~> routes ~> check {
        responseAs[HealthCheck] shouldEqual healthCheck
      }
    }
  }
}
