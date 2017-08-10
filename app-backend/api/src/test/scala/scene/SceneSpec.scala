package com.azavea.rf.api.scene

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
import com.azavea.rf.datamodel._
import java.sql.Timestamp
import java.time.Instant

import geotrellis.vector.{MultiPolygon, Polygon, Point, Geometry}
import geotrellis.slick.Projected

import io.circe._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

class SceneSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {
  implicit val ec = system.dispatcher

  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(5).second)

  val authHeader = AuthUtils.generateAuthHeader("Default")
  val baseScene = "/api/scenes/"
  val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")
  val landsatId = UUID.fromString("697a0b91-b7a8-446e-842c-97cda155554d")
  val sentinelId = UUID.fromString("4a50cb75-815d-4fe5-8bc1-144729ce5b42")

  // Alias to baseRoutes to be explicit
  val baseRoutes = routes

  "/api/scenes/{uuid}" should {

    "return a 404 for non-existent organizations" in {
      Get(s"${baseScene}${publicOrgId}").withHeaders(
        List(authHeader)
      )  ~> Route.seal(baseRoutes) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return a scene" ignore {
      val sceneId = ""
      Get(s"${baseScene}${sceneId}/") ~> baseRoutes ~> check {
        responseAs[Scene.WithRelated]
      }
    }

    "update a scene" ignore {
      // Add change to scene here
    }

    "delete a scene" ignore {
      val sceneId = ""
      Delete(s"${baseScene}${sceneId}/") ~> baseRoutes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
  }

  "/api/scenes/" should {
    "require authentication" in {
      Get("/api/scenes/") ~> baseRoutes ~> check {
        reject
      }
      Get("/api/scenes/").withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]]
      }
    }
    val mpoly = Some(
      Projected(
        MultiPolygon(Polygon(Seq(Point(125.6, 10.1), Point(125.7,10.1), Point(125.7,10.2),
                                 Point(125.6,10.2), Point(125.6,10.1)))), 4326)
    )

    val newSceneDatasource1 = Scene.Create(
      None, publicOrgId, 0, Visibility.Public, List("Test", "Public", "Low Resolution"), landsatId,
      Map("instrument type" -> "satellite", "splines reticulated" -> "0").asJson,
      "test scene datasource 1", None,
      mpoly, mpoly, List.empty[String], List.empty[Image.Banded], List.empty[Thumbnail.Identified], None,
      SceneFilterFields(None,
                        Some(Timestamp.from(Instant.parse("2016-09-19T14:41:58.408544Z"))),
                        None,
                        None),
      SceneStatusFields(JobStatus.Processing, JobStatus.Processing, IngestStatus.NotIngested)
    )

    val newSceneDatasource2 = Scene.Create(
      None, publicOrgId, 0, Visibility.Public, List("Test", "Public", "Low Resolution"), sentinelId,
      Map("instrument type" -> "satellite", "splines reticulated" -> "0").asJson,
      "test scene datasource 2", None, None, None, List.empty[String], List.empty[Image.Banded],
      List.empty[Thumbnail.Identified], Some("an_s3_bucket_location"),
      SceneFilterFields(None, None, None, None),
      SceneStatusFields(JobStatus.Processing, JobStatus.Processing, IngestStatus.Ingested)
    )

    "require authentication for creation" in {
      Post("/api/scenes/").withEntity(
        HttpEntity(
          ContentTypes.`application/json`,
          newSceneDatasource1.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        reject
      }
    }

    "create a scene successfully once authenticated" in {
      Post("/api/scenes/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newSceneDatasource1.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        val sceneWithRelated = responseAs[Scene.WithRelated]
        sceneWithRelated.owner shouldEqual "Default"

        val newSceneDatasource1Image = Image.Banded(
          publicOrgId, 0, Visibility.Public, "filename", "uri", None,
          sceneWithRelated.id, ().asJson, 20.2f, List.empty[String],
          List[Band.Create](Band.Create("i'm a band", 4, List[Int](550, 600)))
        )
        Post("/api/images/").withHeadersAndEntity(
          List(authHeader),
          HttpEntity(
            ContentTypes.`application/json`,
            newSceneDatasource1Image.asJson.noSpaces
          )
        ) ~> baseRoutes ~> check {
          responseAs[Image.WithRelated]
        }
      }

      Post("/api/scenes/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newSceneDatasource2.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        responseAs[Scene.WithRelated]
      }
    }

    "list scenes" in {
      Get(s"${baseScene}?organization=${publicOrgId}").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]]
      }
    }

    "filter by one organization correctly" in {
      Get(s"$baseScene?organization=${publicOrgId}").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 2
      }
    }

    "filter by two organizations correctly" in {
      val url = s"$baseScene?organization=${publicOrgId}&organization=dfac6307-b5ef-43f7-beda-b9f208bb7725"
      Get(url).withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 2
      }
    }

    "filter by one (non-existent) organizations correctly" in {
      val url = s"$baseScene?organization=dfac6307-b5ef-43f7-beda-b9f208bb7725"
      Get(url).withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 0
      }
    }

    "filter by acquisition date correctly (no nulls returned)" in {
      val url = s"$baseScene?minAcquisitionDatetime=2016-09-18T14:41:58.408544Z"
      Get(url).withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 1
      }
    }

    "filter by months correctly" in {
      val urlCorrectMonth = s"$baseScene?month=9"
      Get(urlCorrectMonth).withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 1
      }
      val urlMissingMonth = s"$baseScene?month=10"
      Get(urlMissingMonth).withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 0
      }
    }

    "filter by day-of-month correctly" in {
      val urlMinNoScenes = s"$baseScene?minDayOfMonth=20"
      Get(urlMinNoScenes).withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 0
      }
      val urlMinWithScene = s"$baseScene?minDayOfMonth=18"
      Get(urlMinWithScene).withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 1
      }
      val urlMaxNoScenes = s"$baseScene?maxDayOfMonth=18"
      Get(urlMaxNoScenes).withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 0
      }
      val urlMaxWithScene = s"$baseScene?maxDayOfMonth=20"
      Get(urlMaxWithScene).withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 1
      }
    }

    "filter by one datasource correctly" in {
      val url = s"$baseScene?datasource=$landsatId"
      Get(url).withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 1
      }
    }

    "filter by multiple datasources correctly" in {
      val url = s"$baseScene?datasource=$landsatId&datasource=$sentinelId"
      Get(url).withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 2
      }
    }

    "filter by ingested status correctly" in {
      val url = s"$baseScene?ingestStatus=INGESTED"
      Get(url).withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 1
      }
    }

    "filter by multiple ingested status correctly" in {
      val url = s"$baseScene?ingestStatus=INGESTED&ingestStatus=NOTINGESTED"
      Get(url).withHeaders(List(authHeader)) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 2
      }
    }

    "filter scenes by bounding box" in {
      Get("/api/scenes/?bbox=0,0,0.00001,0.00001").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val res = responseAs[PaginatedResponse[Scene.WithRelated]]
        res.count shouldEqual 0
      }
      /** fully contains the scene */
      Get("/api/scenes/?bbox=100,10,130,20").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val res = responseAs[PaginatedResponse[Scene.WithRelated]]
        res.count shouldEqual 1
      }
      /** partially contains the scene, with another box that doesn't at all */
      Get("/api/scenes/?bbox=100,10,125.6,10.1;0,0,0.001,0.001").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val res = responseAs[PaginatedResponse[Scene.WithRelated]]
        res.count shouldEqual 1
      }
    }

    "filter scenes by point" in {
      Get("/api/scenes/?point=125.65,10.15").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val res = responseAs[PaginatedResponse[Scene.WithRelated]]
        res.count shouldEqual 1
      }
      Get("/api/scenes/?point=1.0,1.0").withHeaders(
        List(authHeader)
      )  ~> baseRoutes ~> check {
        val res = responseAs[PaginatedResponse[Scene.WithRelated]]
        res.count shouldEqual 0
      }
    }

    "filter scenes by image resolution" in {
      Get("/api/scenes/?minResolution=15.0").withHeaders(
        List(authHeader)
      )  ~> baseRoutes ~> check {
        val res = responseAs[PaginatedResponse[Scene.WithRelated]]
        res.count shouldEqual 1
      }

      Get("/api/scenes/?maxResolution=15.0").withHeaders(
        List(authHeader)
      )  ~> baseRoutes ~> check {
        val res = responseAs[PaginatedResponse[Scene.WithRelated]]
        res.count shouldEqual 0
      }
    }

    "sort by one field correctly" in {
      val url = s"$baseScene?sort=datasource,desc"
      Get(url).withHeaders(
        List(authHeader)
      )  ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 2
        responseAs[PaginatedResponse[Scene.WithRelated]].results.head.datasource shouldEqual
          UUID.fromString("697a0b91-b7a8-446e-842c-97cda155554d")
      }
    }

    "sort by two fields correctly" in {
      val url = s"$baseScene?sort=cloudCover,asc;datasource,desc"
      Get(url).withHeaders(
        List(authHeader)
      )  ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 2
        responseAs[PaginatedResponse[Scene.WithRelated]].results.head.datasource shouldEqual
          UUID.fromString("697a0b91-b7a8-446e-842c-97cda155554d")
      }
    }

    "filter by ingest status correctly" in {
      val urlIngested = s"$baseScene?ingested=true"
      Get(urlIngested).withHeaders(
        List(authHeader)
      )  ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 1
      }
      val urlNotIngested = s"$baseScene?ingested=false"
      Get(urlNotIngested).withHeaders(
        List(authHeader)
      )  ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 1
      }
      val urlIgnoreIngested = s"$baseScene"
      Get(urlIgnoreIngested).withHeaders(
        List(authHeader)
      )  ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 2
      }
    }
  }
}
