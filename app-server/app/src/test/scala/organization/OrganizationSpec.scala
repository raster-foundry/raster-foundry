package com.azavea.rf.organization
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}

import com.azavea.rf.utils.{Config, Database}
import com.azavea.rf.{DBSpec, Router}
import com.azavea.rf.datamodel.latest.schema.tables.OrganizationsRow

class OrganizationSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {
  implicit val ec = system.dispatcher
  implicit def database = db

  "The organization service" should {
    "return a list of organizations" in {
      val routes = organizationRoutes
      // TODO: implement tests once we've figured out how to mock JWT tokens for
      //       endpoints that require authentication
      // Get("/api/organizations") ~> routes ~> check {
      //   responseAs[Seq[OrganizationsRow]]
      // }
    }
  }
}
