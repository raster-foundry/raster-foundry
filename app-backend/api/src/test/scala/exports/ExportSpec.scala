package com.azavea.rf.api.exports

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Route
import akka.actor.ActorSystem

import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.Config
import com.azavea.rf.api._
import com.azavea.rf.common._
import com.azavea.rf.api.project.ProjectSpecHelper

import io.circe._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

import scala.concurrent.duration._

class ExportSpec extends WordSpec
  with Matchers
  with ScalatestRouteTest
  with ProjectSpecHelper
  with ExportSpecHelper
  with Config
  with Router
  with DBSpec {
  implicit val ec = system.dispatcher

  implicit def database = db

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(5).second)

  // Alias to baseRoutes to be explicit
  val baseRoutes = routes
  val authHeader = AuthUtils.generateAuthHeader("Default")

  "/api/exports/" should {
    "require authentication" in {
      Get("/api/exports/") ~> baseRoutes ~> check {
        reject
      }
      Get("/api/exports/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val pr = responseAs[PaginatedResponse[Export]]
        pr.count shouldBe 0
      }
    }

    "create an export successfully once authenticated" in {
      Post("/api/projects/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newProject1.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        val p = responseAs[Project]
        projectId = p.id.toString
      }

      Post("/api/exports/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          export.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        val ex = responseAs[Export]
        insertedExport = ex
        exportId = ex.id.toString
        ex.owner shouldEqual "Default"
      }
    }
  }

  "/api/exports/{uuid}/definition" should {
    "return a 404 for non-existent organizations" in {
      Get(s"${baseExport}${publicOrgId}/definition").withHeaders(
        List(authHeader)
      )  ~> Route.seal(baseRoutes) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return an export definition" in {
      Get(s"${baseExport}${exportId}/definition").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val ed = responseAs[ExportDefinition]
        ed.id.toString shouldBe exportId
      }
    }
  }

  "/api/exports/{uuid}" should {
    "return a 404 for non-existent organizations" in {
      Get(s"${baseExport}${publicOrgId}").withHeaders(
        List(authHeader)
      )  ~> Route.seal(baseRoutes) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return an export" in {
      Get(s"${baseExport}${exportId}").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[Export]
      }
    }

    "update an export" in {
      Put(s"${baseExport}${exportId}").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          updatedExport.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "return a correct export after update" in {
      Get(s"${baseExport}${exportId}").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val ex = responseAs[Export]
        ex.id shouldEqual updatedExport.id
        ex.exportStatus shouldEqual updatedExport.exportStatus
      }
    }

    "delete an export" in {
      Delete(s"${baseExport}${exportId}/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
  }
}
