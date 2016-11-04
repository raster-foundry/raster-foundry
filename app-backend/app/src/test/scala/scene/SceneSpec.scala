package com.azavea.rf.scene

import java.util.UUID

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.http.scaladsl.server.Route
import akka.actor.ActorSystem
import concurrent.duration._
import spray.json._

import com.azavea.rf.utils.Config
import com.azavea.rf.{DBSpec, Router}
import com.azavea.rf.datamodel._
import com.azavea.rf.AuthUtils
import java.sql.Timestamp
import java.time.Instant

import geotrellis.vector.{MultiPolygon, Polygon, Point, Geometry}
import geotrellis.slick.Projected


class SceneSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {
  implicit val ec = system.dispatcher

  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(20).second)

  val authorization = AuthUtils.generateAuthHeader("Default")
  val baseScene = "/api/scenes/"
  val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")

  // Alias to baseRoutes to be explicit
  val baseRoutes = routes

  "/api/scenes/{uuid}" should {

    "return a 404 for non-existent organizations" in {
      Get(s"${baseScene}${publicOrgId}") ~> Route.seal(baseRoutes) ~> check {
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
    "not require authentication" in {
      Get("/api/scenes/") ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]]
      }
    }
    val mpoly = Some(Projected(
      MultiPolygon(Polygon(Seq(Point(100,100), Point(110,100), Point(110,110),
        Point(100,110), Point(100,100)))), 3857))

    val newSceneDatasource1Image = Image.Identified(
      None, 0, Visibility.Public, "newSceneDatasource1Image", "",
      Map.empty[String, Any], 20f, List.empty[String]
    )

    val newSceneDatasource1 = Scene.Create(
      publicOrgId, 0, Visibility.Public, List("Test", "Public", "Low Resolution"), "TEST_ORG",
      Map("instrument type" -> "satellite", "splines reticulated" -> 0):Map[String, Any], None,
      Some(Timestamp.from(Instant.parse("2016-09-19T14:41:58.408544Z"))),
      JobStatus.Processing, JobStatus.Processing, JobStatus.Processing, None, None, "test scene datasource 1",
      mpoly, List.empty[String], List(newSceneDatasource1Image), List.empty[Thumbnail.Identified]
    )

    val newSceneDatasource2 = Scene.Create(
      publicOrgId, 0, Visibility.Public, List("Test", "Public", "Low Resolution"),
      "TEST_ORG-OTHER", Map("instrument type" -> "satellite", "splines reticulated" -> 0):Map[String, Any],
      None, None, JobStatus.Processing, JobStatus.Processing, JobStatus.Processing, None, None, "test scene datasource 2",
      None, List.empty[String], List.empty[Image.Identified], List.empty[Thumbnail.Identified]
    )

    "require authentication for creation" in {
      Post("/api/scenes/").withEntity(
        HttpEntity(
          ContentTypes.`application/json`,
          newSceneDatasource1.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        reject
      }
    }

    "create a scene successfully once authenticated" in {
      Post("/api/scenes/").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newSceneDatasource1.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        responseAs[Scene.WithRelated]
      }

      Post("/api/scenes/").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newSceneDatasource2.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        responseAs[Scene.WithRelated]
      }
    }

    "filter by one organization correctly" in {
      Get(s"$baseScene?organization=${publicOrgId}") ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 2
      }
    }

    "filter by two organizations correctly" in {
      val url = s"$baseScene?organization=${publicOrgId}&organization=dfac6307-b5ef-43f7-beda-b9f208bb7725"
      Get(url) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 2
      }
    }

    "filter by one (non-existent) organizations correctly" in {
      val url = s"$baseScene?organization=dfac6307-b5ef-43f7-beda-b9f208bb7725"
      Get(url) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 0
      }
    }

    "filter by acquisition date correctly (no nulls returned)" in {
      val url = s"$baseScene?minAcquisitionDatetime=2016-09-18T14:41:58.408544z"
      Get(url) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 1
      }
    }

    "filter by months correctly" in {
      val urlCorrectMonth = s"$baseScene?month=9"
      Get(urlCorrectMonth) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 1
      }
      val urlMissingMonth = s"$baseScene?month=10"
      Get(urlMissingMonth) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 0
      }
    }

    "filter by one datasource correctly" in {
      val url = s"$baseScene?datasource=TEST_ORG"
      Get(url) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 1
      }
    }

    "filter by multiple datasources correctly" in {
      val url = s"$baseScene?datasource=TEST_ORG&datasource=TEST_ORG-OTHER"
      Get(url) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 2
      }
    }

    "filter scenes by bounding box" in {
      Get("/api/scenes/?bbox=0,0,0.00001,0.00001") ~> baseRoutes ~> check {
        val res = responseAs[PaginatedResponse[Scene.WithRelated]]
        res.count shouldEqual 0
      }
      Get("/api/scenes/?bbox=0,0,0.001,0.001") ~> baseRoutes ~> check {
        val res = responseAs[PaginatedResponse[Scene.WithRelated]]
        res.count shouldEqual 1
      }
    }

    "filter scenes by point" in {
      Get("/api/scenes/?point=0.0009,0.0009") ~> baseRoutes ~> check {
        val res = responseAs[PaginatedResponse[Scene.WithRelated]]
        res.count shouldEqual 1
      }
      Get("/api/scenes/?point=1.0,1.0") ~> baseRoutes ~> check {
        val res = responseAs[PaginatedResponse[Scene.WithRelated]]
        res.count shouldEqual 0
      }
    }

    "filter scenes by image resolution" in {
      Get("/api/scenes/?minResolution=15.0") ~> baseRoutes ~> check {
        val res = responseAs[PaginatedResponse[Scene.WithRelated]]
        res.count shouldEqual 1
      }

      Get("/api/scenes/?maxResolution=15.0") ~> baseRoutes ~> check {
        val res = responseAs[PaginatedResponse[Scene.WithRelated]]
        res.count shouldEqual 0
      }
    }

    "sort by one field correctly" in {
      val url = s"$baseScene?sort=datasource,desc"
      Get(url) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 2
        responseAs[PaginatedResponse[Scene.WithRelated]].results.head.datasource shouldEqual "TEST_ORG-OTHER"
      }
    }

    "sort by two fields correctly" in {
      val url = s"$baseScene?sort=cloudCover,asc;datasource,desc"
      Get(url) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 2
        responseAs[PaginatedResponse[Scene.WithRelated]].results.head.datasource shouldEqual "TEST_ORG-OTHER"
      }
    }
  }
}
