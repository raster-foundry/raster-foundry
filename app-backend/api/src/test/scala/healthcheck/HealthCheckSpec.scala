package com.azavea.rf.api.healthcheck

import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import org.scalatest.{Matchers, WordSpec}
import akka.actor.ActorSystem
import concurrent.duration._

import com.azavea.rf.api.utils.Config
import com.azavea.rf.api.Router
import com.azavea.rf.common._
import com.azavea.rf.api.Codec._

import io.circe._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

class HealthCheckSpec extends WordSpec
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

  "The healthcheck service" should {
    "return an OK status" in {
      val dbCheck = ServiceCheck("database", HealthCheckStatus.OK)
      val healthCheck = HealthCheck(HealthCheckStatus.OK, Seq(dbCheck))
      Get("/healthcheck") ~> baseRoutes ~> check {
        responseAs[HealthCheck] shouldEqual healthCheck
      }
    }
  }
}
