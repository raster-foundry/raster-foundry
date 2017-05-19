package com.azavea.rf.api.tooltag

import java.util.UUID

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.actor.ActorSystem
import org.scalatest.{Matchers, WordSpec}

import concurrent.duration._

import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.Config
import com.azavea.rf.api._
import com.azavea.rf.common._

import io.circe._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

class ToolTagSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {

  implicit val ec = system.dispatcher
  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(5).second)

  val authHeader = AuthUtils.generateAuthHeader("Default")
  val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")
  val baseToolTag = "/tool-tags/"
  val newToolTag = ToolTag.Create(
    publicOrgId,
    "test tag",
    None: Option[String]
  )

  val newToolTagBadOwner = ToolTag.Create(
    publicOrgId,
    "bad owner",
    Some("Not a real user")
  )

  // Alias to baseRoutes to be explicit
  val baseRoutes = routes

  "/api/tool-tags/{uuid}" should {
    "return a 404 for non-existent tool tag" ignore {
      Get(s"${baseToolTag}${publicOrgId}") ~> Route.seal(baseRoutes) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "update a tool tag" ignore {
      // TODO: Add tool update when DB interaction is fixed
    }

    "delete a tool tag" ignore {
      val toolTagId = ""
      Delete(s"${baseToolTag}${toolTagId}/") ~> baseRoutes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
  }

  "/api/tool-tags/" should {

    "reject creating tool tags without authentication" in {
      Post("/api/tool-tags/").withEntity(
        HttpEntity(
          ContentTypes.`application/json`,
          newToolTag.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        reject
      }
    }

    "reject setting owner to another user" in {
      Post("/api/tool-tags/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newToolTagBadOwner.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        reject
      }
    }

    "create a tool tag with authHeader" in {
      Post("/api/tool-tags/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newToolTag.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        val tt = responseAs[ToolTag]
        tt.owner shouldEqual "Default"
      }
    }

    "require authentication for list" in {
      Get("/api/tool-tags/") ~> baseRoutes ~> check {
        reject
      }
    }
  }
}
