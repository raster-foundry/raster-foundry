package com.azavea.rf.api.toolrun

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.Config
import com.azavea.rf.api._
import com.azavea.rf.common._
import concurrent.duration._
import org.scalatest.{Matchers, WordSpec}

import io.circe._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

class ToolRunSpec extends WordSpec
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
  // Non-functional UUIDs
  val toolId = UUID.fromString("e609629f-05f5-4b18-a0e9-ea612b3c9ed7")
  val baseToolRun = "/tool-runs/"
  val newToolRun = ToolRun.Create(
    Visibility.Public,
    publicOrgId,
    toolId,
    ().asJson,
    None: Option[String]
  )

  val baseRoutes = routes

  "/api/tool-runs/{uuid}" should {
    "return a 404 for non-existent tool-run" in {
      Get(s"${baseToolRun}${publicOrgId}") ~> Route.seal(baseRoutes) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "update a tool-run" ignore {
      // TODO: Add updating when creating associated objects doesn't require tons of boilerplate
    }

    "delete a tool-run" ignore {
      // TODO: Also add when it is ergonomic to create test objects
      val toolRunId = ""
      Delete(s"${baseToolRun}${toolRunId}/") ~> baseRoutes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
  }

  "/api/tool-runs/" should {

    "reject creating tool-runs without authentication" in {
      Post("/api/tool-runs/").withEntity(
        HttpEntity(
          ContentTypes.`application/json`,
          newToolRun.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        reject
      }
    }

    "create a tool-run with authorization" ignore {
      // TODO: Add creation when creating referenced objects is fixed, see above
      Post("/api/tool-runs/").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newToolRun.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        val tr = responseAs[ToolRun]
        tr.owner shouldEqual "Default"
      }
    }

    "require authentication for list" in {
      Get("/api/tool-runs/") ~> baseRoutes ~> check {
        reject
      }
    }
  }
}
