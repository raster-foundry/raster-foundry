package com.azavea.rf.tool

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import com.azavea.rf.datamodel._
import com.azavea.rf.utils.Config
import com.azavea.rf.{AuthUtils, DBSpec, Router}
import concurrent.duration._
import org.scalatest.{Matchers, WordSpec}
import spray.json._

class ToolSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {

  implicit val ec = system.dispatcher
  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(5).second)

  val authorization = AuthUtils.generateAuthHeader("Default")
  val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")
  val baseTool = "/tools/"
  val newTool = Tool.Create(
    publicOrgId,
    "Test tool label",
    "Test tool description",
    "Test tool requirements",
    "Test tool license",
    Visibility.Public,
    List("Test tool datasource"),
    2.5f,
    Map(),
    List(),
    List()
)

  // Alias to baseRoutes to be explicit
  val baseRoutes = routes

  "/api/tools/{uuid}" should {
    "return a 404 for non-existent tool" ignore {
      Get(s"${baseTool}${publicOrgId}") ~> Route.seal(baseRoutes) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "update a tool" ignore {
      // TODO: Add tool update when DB interaction is fixed
    }

    "delete a tool" ignore {
      val toolId = ""
      Delete(s"${baseTool}${toolId}/") ~> baseRoutes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
  }

  "/api/tools/" should {

    "reject creating tools without authentication" in {
      Post("/api/tools/").withEntity(
        HttpEntity(
          ContentTypes.`application/json`,
          newTool.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        reject
      }
    }

    "create a tool with authorization" in {
      Post("/api/tools/").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newTool.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        responseAs[Tool]
      }
    }

    "require authentication for list" in {
      Get("/api/tools/") ~> baseRoutes ~> check {
        reject
      }
    }
  }
}
