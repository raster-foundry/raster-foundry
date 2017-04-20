package com.azavea.rf.api.thumbnail

import java.sql.Timestamp
import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.server.Route
import akka.actor.ActorSystem
import org.scalatest.{Matchers, WordSpec}

import concurrent.duration._

import com.azavea.rf.api.AuthUtils
import com.azavea.rf.database.tables._
import com.azavea.rf.datamodel._
import com.azavea.rf.api.scene._
import com.azavea.rf.api.utils.Config
import com.azavea.rf.api.Router
import com.azavea.rf.common._

import io.circe._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

import scala.util.{Success, Failure, Try}

class ThumbnailSpec extends WordSpec
    with ThumbnailSpecHelper
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {

  implicit val ec = system.dispatcher
  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(5).second)

  val uuid = new UUID(123456789, 123456789)
  val baseThumbnailRow = Thumbnail(
    uuid,
    new Timestamp(1234687268),
    new Timestamp(1234687268),
    uuid,
    128,
    128,
    uuid,
    "https://website.com",
    ThumbnailSize.Large
  )
  val authHeader = AuthUtils.generateAuthHeader("Default")

  // Alias to baseRoutes to be explicit
  val baseRoutes = routes

  "/api/thumbnails/{uuid}" should {
    "return a 404 for non-existent thumbnail" ignore {
      Get(s"${baseThumbnail}${publicOrgId}") ~> Route.seal(baseRoutes) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return a thumbnail" ignore {
      val thumbnailId = ""
      Get(s"${baseThumbnail}${thumbnailId}/") ~> baseRoutes ~> check {
        responseAs[Thumbnail]
      }
    }

    "update a thumbnail" ignore {
      // Add change to thumbnail here
    }

    "delete a thumbnail" ignore {
      val thumbnailId = ""
      Delete(s"${baseThumbnail}${thumbnailId}/") ~> baseRoutes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
  }

  "/api/thumbnails/" should {
    "have a scene to work with" in {
      Post("/api/scenes/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newScene.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        responseAs[Scene.WithRelated]
      }
    }

    "require authentication for list" in {
      Get("/api/thumbnails/") ~> baseRoutes ~> check {
        reject
      }
      Get("/api/thumbnails/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Thumbnail]]
      }
    }


    "create thumbnails only with authentication" in {
      Get("/api/scenes/").withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        val scenes = responseAs[PaginatedResponse[Scene.WithRelated]]
        val sceneId = scenes.results.head.id
        val thumbnailToPost1 = newThumbnail(ThumbnailSize.Small, sceneId)
        val thumbnailToPost2 = newThumbnail(ThumbnailSize.Square, sceneId)

        Post("/api/thumbnails/").withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            thumbnailToPost1.asJson.noSpaces
          )
        ) ~> baseRoutes ~> check {
          reject
        }

        Post("/api/thumbnails/").withHeadersAndEntity(
          List(authHeader),
          HttpEntity(
            ContentTypes.`application/json`,
            thumbnailToPost1.asJson.noSpaces
          )
        ) ~> baseRoutes ~> check {
          responseAs[Thumbnail]
        }

        Post("/api/thumbnails/").withHeadersAndEntity(
          List(authHeader),
          HttpEntity(
            ContentTypes.`application/json`,
            thumbnailToPost2.asJson.noSpaces
          )
        ) ~> baseRoutes ~> check {
          responseAs[Thumbnail]
        }
      }
    }

    "filter by one scene correctly" in {
      Get("/api/scenes/").withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        val scenes = responseAs[PaginatedResponse[Scene.WithRelated]]
        val sceneId = scenes.results.head.id
        Get(s"/api/thumbnails/?sceneId=$sceneId").withHeaders(
          List(authHeader)
        ) ~> baseRoutes ~> check {
          responseAs[PaginatedResponse[Thumbnail]].count shouldEqual 2
        }
      }
    }

    "filter by one (non-existent) scene correctly" in {
      val url = s"/api/thumbnails/?sceneId=${UUID.randomUUID}"
      Get(url).withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Thumbnail]].count shouldEqual 0
      }
    }

    "sort by one field correctly" ignore {
      val url = s"/api/thumbnails/?sort=..."
      Get(url).withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        /** Sorting behavior isn't described in the spec currently but might be someday */
        responseAs[PaginatedResponse[Thumbnail]]
      }
    }
  }
}
