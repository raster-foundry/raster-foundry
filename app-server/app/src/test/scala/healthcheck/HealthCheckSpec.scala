package com.azavea.rf.healthcheck

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}

import com.azavea.rf.utils.{Config, Database}
import com.azavea.rf.Router


class HealthCheckSpec extends WordSpec with Matchers with ScalatestRouteTest with Config with Router {

  implicit val ec = system.dispatcher
  implicit val database = new Database(jdbcUrl, dbUser, dbPassword)

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
