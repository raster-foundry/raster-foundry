package com.azavea.rf.api.image

import java.util.UUID

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.http.scaladsl.server.Route
import akka.actor.ActorSystem
import concurrent.duration._

import com.azavea.rf.api.utils.Config
import com.azavea.rf.api._
import com.azavea.rf.common._
import com.azavea.rf.api.scene._
import com.azavea.rf.datamodel._
import java.sql.Timestamp
import java.time.Instant

import io.circe._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

class ImageSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {
  implicit val ec = system.dispatcher

  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(5).second)

  val authHeader = AuthUtils.generateAuthHeader("Default")
  val baseImagePath = "/api/images/"
  val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")
  val landsatId = UUID.fromString("697a0b91-b7a8-446e-842c-97cda155554d")

  val newSceneDatasource1 = Scene.Create(
    None, publicOrgId, 0, Visibility.Public, List("Test", "Public", "Low Resolution"), landsatId,
    Map("instrument type" -> "satellite", "splines reticulated" -> "0").asJson,
    "test scene image spec 1", None: Option[String],
    None, None, List.empty[String], List.empty[Image.Banded],
    List.empty[Thumbnail.Identified], None,
    SceneFilterFields(None,
                      Some(Timestamp.from(Instant.parse("2016-09-19T14:41:58.408544Z"))),
                      None,
                      None),
    SceneStatusFields(JobStatus.Processing, JobStatus.Processing, IngestStatus.NotIngested)
  )

  // Alias to baseRoutes to be explicit
  val baseRoutes = routes


  "/api/images/{uuid}" should {

    "return a 404 for non-existent image" in {
      Get(s"${baseImagePath}${publicOrgId}").withHeaders(
        List(authHeader)
      ) ~> Route.seal(baseRoutes) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return a image" ignore {
      val imageId = ""
      Get(s"${baseImagePath}${imageId}/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[Image]
      }
    }

    "update a image" ignore {
      // Add change to image here
    }

    "delete a image" ignore {
      val imageId = ""
      Delete(s"${baseImagePath}${imageId}/") ~> baseRoutes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
  }

  "/api/images/" should {
    "require authentication" in {
      Get("/api/images/") ~> baseRoutes ~> check {
        reject
      }
      Get("/api/images/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Image.WithRelated]]
      }
    }

    "create an image successfully once authenticated" in {
      // Create scene first via API because we need the ID
      Post("/api/scenes/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newSceneDatasource1.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        val sceneId = responseAs[Scene.WithRelated].id

        val newImageDatasource1 = Image.Banded(
          publicOrgId, 1024, Visibility.Public, "test-image.png", "s3://public/s3/test-image.png",
          None, sceneId,
          Map("instrument type" -> "satellite", "splines reticulated" -> "0").asJson,
          20.2f, List.empty[String], List[Band.Create](Band.Create("name", 3, List(100, 250)))
        )

        Post("/api/images/").withHeadersAndEntity(
          List(authHeader),
          HttpEntity(
            ContentTypes.`application/json`,
            newImageDatasource1.asJson.noSpaces
          )
        ) ~> baseRoutes ~> check {
          val image = responseAs[Image.WithRelated]
          image.owner shouldEqual "Default"
        }
      }
    }

    "filter by one organization correctly" in {
      Get(s"$baseImagePath?organization=${publicOrgId}").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Image.WithRelated]].count shouldEqual 1
      }
    }

    "filter by two organizations correctly" in {
      val url = s"$baseImagePath?organization=${publicOrgId}&organization=dfac6307-b5ef-43f7-beda-b9f208bb7725"
      Get(url).withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Image.WithRelated]].count shouldEqual 1
      }
    }

    "filter by one (non-existent) organizations correctly" in {
      val url = s"$baseImagePath?organization=dfac6307-b5ef-43f7-beda-b9f208bb7725"
      Get(url).withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Image.WithRelated]].count shouldEqual 0
      }
    }

    "filter by min bytes correctly" in {
      val url = s"$baseImagePath?minRawDataBytes=10"
      Get(url).withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Image.WithRelated]].count shouldEqual 1
      }
    }

    "filter by scene correctly" in {
      Get("/api/scenes/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val scenes = responseAs[PaginatedResponse[Scene.WithRelated]]
        val sceneId = scenes.results.head.id

        val url = s"$baseImagePath?scene=$sceneId"
        Get(url).withHeaders(
          List(authHeader)
        ) ~> baseRoutes ~> check {
          responseAs[PaginatedResponse[Image.WithRelated]].count shouldEqual 1
        }
      }
    }
  }
}
