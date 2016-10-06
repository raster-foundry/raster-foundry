package com.azavea.rf.thumbnail

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import concurrent.duration._
import org.scalatest.{Matchers, WordSpec}
import spray.json._

import slick.lifted._

import com.azavea.rf.AuthUtils
import com.azavea.rf.datamodel.enums._
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.scene._
import com.azavea.rf.utils.Config
import com.azavea.rf.utils.PaginatedResponse
import com.azavea.rf.{DBSpec, Router}

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

  val uuid = new UUID(123456789, 123456789)
  val baseThumbnailRow = ThumbnailsRow(
    uuid,
    new Timestamp(1234687268),
    new Timestamp(1234687268),
    uuid,
    128,
    128,
    uuid,
    "https://website.com",
    LARGE
  )

  "Creating a row" should {
    "add a row to the table" ignore {
      val result = ThumbnailService.insertThumbnail(baseThumbnailRow)
      assert(result === Success)
    }
  }

  "Getting a row" should {
    "return the expected row" ignore {
      assert(ThumbnailService.getThumbnail(uuid) === baseThumbnailRow)
    }
  }

  "Updating a row" should {
    "change the expected values" ignore {
      val newThumbnailsRow = ThumbnailsRow(
        uuid,
        new Timestamp(1234687268),
        new Timestamp(1234687268),
        uuid,
        256,
        128,
        uuid,
        "https://website.com",
        LARGE
      )
      val result = ThumbnailService.updateThumbnail(newThumbnailsRow, uuid)
      assert(result === 1)
      ThumbnailService.getThumbnail(uuid) map {
        case Some(resp) => assert(resp.widthPx === 256)
        case _ => Failure(new Exception("Field not updated successfully"))
      }
    }
  }

  "Deleting a row" should {
    "remove a row from the table" ignore {
      val result = ThumbnailService.deleteThumbnail(uuid)
      assert(result === 1)
    }
  }


  "/api/thumbnails/{uuid}" should {
    "return a 404 for non-existent thumbnail" ignore {
      Get(s"${baseThumbnail}${publicOrgId}") ~> thumbnailRoutes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return a thumbnail" ignore {
      val thumbnailId = ""
      Get(s"${baseThumbnail}${thumbnailId}/") ~> thumbnailRoutes ~> check {
        responseAs[ThumbnailsRow]
      }
    }

    "update a thumbnail" ignore {
      // Add change to thumbnail here
    }

    "delete a thumbnail" ignore {
      val thumbnailId = ""
      Delete(s"${baseThumbnail}${thumbnailId}/") ~> thumbnailRoutes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
  }

  "/api/thumbnails/" should {
    "have a scene to work with" in {
      Post("/api/scenes/").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newScene.toJson(createSceneFormat).toString()
        )
      ) ~> sceneRoutes ~> check {
        responseAs[SceneWithRelated]
      }
    }

    "not require authentication for list" in {
      Get("/api/thumbnails/") ~> thumbnailRoutes ~> check {
        responseAs[PaginatedResponse[ThumbnailsRow]]
      }
    }


    "create thumbnails only with authentication" in {
      Get("/api/scenes/") ~> sceneRoutes ~> check {
        val scenes = responseAs[PaginatedResponse[SceneWithRelated]]
        val sceneId = scenes.results.head.id
        val thumbnailToPost1 = newThumbnail(SMALL, sceneId)
        val thumbnailToPost2 = newThumbnail(SQUARE, sceneId)

        Post("/api/thumbnails/").withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            thumbnailToPost1.toJson(createThumbnailFormat).toString()
          )
        ) ~> thumbnailRoutes ~> check {
          reject
        }

        Post("/api/thumbnails/").withHeadersAndEntity(
          List(authorization),
          HttpEntity(
            ContentTypes.`application/json`,
            thumbnailToPost1.toJson(createThumbnailFormat).toString()
          )
        ) ~> thumbnailRoutes ~> check {
          responseAs[ThumbnailsRow]
        }

        Post("/api/thumbnails/").withHeadersAndEntity(
          List(authorization),
          HttpEntity(
            ContentTypes.`application/json`,
            thumbnailToPost2.toJson(createThumbnailFormat).toString()
          )
        ) ~> thumbnailRoutes ~> check {
          responseAs[ThumbnailsRow]
        }
      }
    }

    "filter by one scene correctly" in {
      Get("/api/scenes/") ~> sceneRoutes ~> check {
        val scenes = responseAs[PaginatedResponse[SceneWithRelated]]
        val sceneId = scenes.results.head.id
        Get(s"/api/thumbnails/?sceneId=$sceneId") ~> thumbnailRoutes ~> check {
          responseAs[PaginatedResponse[ThumbnailsRow]].count shouldEqual 2
        }
      }
    }

    "filter by one (non-existent) scene correctly" in {
      val url = s"/api/thumbnails/?sceneId=${UUID.randomUUID}"
      Get(url) ~> thumbnailRoutes ~> check {
        responseAs[PaginatedResponse[ThumbnailsRow]].count shouldEqual 0
      }
    }

    "sort by one field correctly" ignore {
      val url = s"/api/thumbnails/?sort=..."
      Get(url) ~> thumbnailRoutes ~> check {
        /** Sorting behavior isn't described in the spec currently but might be someday */
        responseAs[PaginatedResponse[ThumbnailsRow]]
      }
    }
  }
}
