package com.azavea.rf.modeltag

import java.util.UUID

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.azavea.rf.datamodel._
import com.azavea.rf.utils.Config
import com.azavea.rf.{AuthUtils, DBSpec, Router}
import org.scalatest.{Matchers, WordSpec}
import spray.json._

class ModelTagSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {

  implicit val ec = system.dispatcher
  implicit def database = db

  val authHeader = AuthUtils.generateAuthHeader("Default")
  val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")
  val baseModelTag = "/model-tags/"
  val newModelTag = ModelTag.Create(
    publicOrgId,
    "test tag"
  )

  // Alias to baseRoutes to be explicit
  val baseRoutes = routes

  "/api/model-tags/{uuid}" should {
    "return a 404 for non-existent model tag" ignore {
      Get(s"${baseModelTag}${publicOrgId}") ~> Route.seal(baseRoutes) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "update a model tag" ignore {
      // TODO: Add model update when DB interaction is fixed
    }

    "delete a model tag" ignore {
      val modelTagId = ""
      Delete(s"${baseModelTag}${modelTagId}/") ~> baseRoutes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
  }

  "/api/model-tags/" should {

    "reject creating model tags without authentication" in {
      Post("/api/model-tags/").withEntity(
        HttpEntity(
          ContentTypes.`application/json`,
          newModelTag.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        reject
      }
    }

    "create a model tag with authHeader" in {
      Post("/api/model-tags/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newModelTag.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        responseAs[ModelTag]
      }
    }

    "require authentication for list" in {
      Get("/api/model-tags/") ~> baseRoutes ~> check {
        reject
      }
    }
  }
}
