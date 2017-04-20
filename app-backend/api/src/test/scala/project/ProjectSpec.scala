package com.azavea.rf.api.project

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.http.scaladsl.server.Route
import akka.actor.ActorSystem
import concurrent.duration._

import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.Config
import com.azavea.rf.api._
import com.azavea.rf.common._

import io.circe._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

class ProjectSpec extends WordSpec
    with ProjectSpecHelper
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
  val authHeader = AuthUtils.generateAuthHeader("Default")

  "/api/projects/{uuid}" should {

    "return a 404 for non-existent project" in {
      Get(s"${baseProject}${publicOrgId}").withHeaders(
        List(authHeader)
      ) ~> Route.seal(baseRoutes) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return a project" ignore {
      val projectId = ""
      Get(s"${baseProject}${projectId}/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[Project]
      }
    }

    "update a project" ignore {
      // Add change to project here
    }

    "delete a project with authentication" ignore {
      val projectId = ""
      Delete(s"${baseProject}${projectId}/") ~> baseRoutes ~> check {
        reject
      }
      Delete(s"${baseProject}${projectId}/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
  }

  "/api/projects/" should {
    "require authentication" in {
      Get("/api/projects/") ~> baseRoutes ~> check {
        reject
      }
      Get("/api/projects/").withHeaders(
        List(authHeader)
      )  ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Project]]
      }
    }

    "require authentication for creation" in {
      Post("/api/projects/").withEntity(
        HttpEntity(
          ContentTypes.`application/json`,
          newProject1.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        reject
      }
    }

    "create a project successfully once authenticated" in {
      Post("/api/projects/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newProject1.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        val p = responseAs[Project]
        p.owner shouldEqual "Default"
      }

      Post("/api/projects/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newProject2.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        val p = responseAs[Project]
        p.owner shouldEqual "Default"
      }
    }

    // TODO: https://github.com/azavea/raster-foundry/issues/712
    "filter by one organization correctly" ignore {
      Get(s"/api/projects/?organization=${publicOrgId}").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Project]].count shouldEqual 2
      }
    }

    // TODO: https://github.com/azavea/raster-foundry/issues/712
    "filter by two organizations correctly" ignore {
      val url = s"/api/projects/?organization=${publicOrgId}&organization=${fakeOrgId}"
      Get(url).withHeaders(
        List(authHeader)
      )  ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Project]].count shouldEqual 2
      }
    }

    "filter by one (non-existent) organizations correctly" in {
      val url = s"/api/projects/?organization=${fakeOrgId}"
      Get(url).withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Project]].count shouldEqual 0
      }
    }

    // TODO: https://github.com/azavea/raster-foundry/issues/712
    "filter by created by real user correctly" ignore {
      val url = s"/api/projects/?createdBy=Default"
      Get(url).withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Project]].count shouldEqual 2
      }
    }

    "filter by created by fake user correctly" in {
      val url = s"/api/projects/?createdBy=IsNotReal"
      Get(url).withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Project]].count shouldEqual 0
      }
    }

    "sort by one field correctly" in {
      val url = s"/api/projects/?sort=name,desc"
      Get(url).withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Project]].count shouldEqual 2
        responseAs[PaginatedResponse[Project]].results.head.name shouldEqual "Test Two"
      }
    }

    "sort by two fields correctly" in {
      val url = s"/api/projects/?sort=visibility,asc;name,desc"
      Get(url).withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Project]].count shouldEqual 2
        responseAs[PaginatedResponse[Project]].results.head.name shouldEqual "Test Two"
      }
    }
  }
}
