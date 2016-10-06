package com.azavea.rf.scene

import java.util.UUID

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.actor.ActorSystem
import concurrent.duration._
import spray.json._

import com.azavea.rf.utils.Config
import com.azavea.rf.{DBSpec, Router}
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.datamodel.enums._
import com.azavea.rf.utils.PaginatedResponse
import com.azavea.rf.AuthUtils
import java.sql.Timestamp
import java.time.Instant

import geotrellis.vector.io._
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


  "/api/scenes/{uuid}" should {

    "return a 404 for non-existent organizations" in {
      Get(s"${baseScene}${publicOrgId}") ~> sceneRoutes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return a scene" ignore {
      val sceneId = ""
      Get(s"${baseScene}${sceneId}/") ~> sceneRoutes ~> check {
        responseAs[SceneWithRelated]
      }
    }

    "update a scene" ignore {
      // Add change to scene here
    }

    "delete a scene" ignore {
      val sceneId = ""
      Delete(s"${baseScene}${sceneId}/") ~> sceneRoutes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
  }

  "/api/scenes/" should {
    "not require authentication" in {
      Get("/api/scenes/") ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[SceneWithRelated]]
      }
    }
    val mpoly = Some(Projected(
      MultiPolygon(Polygon(Seq(Point(100,100), Point(110,100), Point(110,110),
        Point(100,110), Point(100,100)))), 3857))

    val newSceneDatasource1 = CreateScene(
      publicOrgId, 0, PUBLIC, 20.2f, List("Test", "Public", "Low Resolution"), "TEST_ORG",
      Map("instrument type" -> "satellite", "splines reticulated" -> 0):Map[String, Any], None,
      Some(Timestamp.from(Instant.parse("2016-09-19T14:41:58.408544Z"))),
      PROCESSING, PROCESSING, PROCESSING, None, None, "test scene datasource 1",
      List(): List[SceneImage], mpoly, List(): List[SceneThumbnail]
    )

    val newSceneDatasource2 = CreateScene(
      publicOrgId, 0, PUBLIC, 20.2f, List("Test", "Public", "Low Resolution"),
      "TEST_ORG-OTHER", Map("instrument type" -> "satellite", "splines reticulated" -> 0):Map[String, Any],
      None, None, PROCESSING, PROCESSING, PROCESSING, None, None, "test scene datasource 2",
      List(): List[SceneImage], None, List(): List[SceneThumbnail]
    )

    "require authentication for creation" in {
      Post("/api/scenes/").withEntity(
        HttpEntity(
          ContentTypes.`application/json`,
          newSceneDatasource1.toJson(createSceneFormat).toString()
        )
      ) ~> sceneRoutes ~> check {
        reject
      }
    }

    "create a scene successfully once authenticated" in {
      Post("/api/scenes/").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newSceneDatasource1.toJson(createSceneFormat).toString()
        )
      ) ~> sceneRoutes ~> check {
        responseAs[SceneWithRelated]
      }

      Post("/api/scenes/").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newSceneDatasource2.toJson(createSceneFormat).toString()
        )
      ) ~> sceneRoutes ~> check {
        responseAs[SceneWithRelated]
      }
    }

    "filter by one organization correctly" in {
      Get(s"$baseScene?organization=${publicOrgId}") ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[SceneWithRelated]].count shouldEqual 2
      }
    }

    "filter by two organizations correctly" in {
      val url = s"$baseScene?organization=${publicOrgId}&organization=dfac6307-b5ef-43f7-beda-b9f208bb7725"
      Get(url) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[SceneWithRelated]].count shouldEqual 2
      }
    }

    "filter by one (non-existent) organizations correctly" in {
      val url = s"$baseScene?organization=dfac6307-b5ef-43f7-beda-b9f208bb7725"
      Get(url) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[SceneWithRelated]].count shouldEqual 0
      }
    }

    "filter by acquisition date correctly (no nulls returned)" in {
      val url = s"$baseScene?minAcquisitionDatetime=2016-09-18T14:41:58.408544z"
      Get(url) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[SceneWithRelated]].count shouldEqual 1
      }
    }

    "filter by months correctly" in {
      val urlCorrectMonth = s"$baseScene?month=9"
      Get(urlCorrectMonth) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[SceneWithRelated]].count shouldEqual 1
      }
      val urlMissingMonth = s"$baseScene?month=10"
      Get(urlMissingMonth) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[SceneWithRelated]].count shouldEqual 0
      }
    }

    "filter by one datasource correctly" in {
      val url = s"$baseScene?datasource=TEST_ORG"
      Get(url) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[SceneWithRelated]].count shouldEqual 1
      }
    }

    "filter by multiple datasources correctly" in {
      val url = s"$baseScene?datasource=TEST_ORG&datasource=TEST_ORG-OTHER"
      Get(url) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[SceneWithRelated]].count shouldEqual 2
      }
    }

    "filter scenes by bounding box" in {
      Get("/api/scenes/?bbox=0,0,1,1") ~> sceneRoutes ~> check {
        val res = responseAs[PaginatedResponse[SceneWithRelated]]
        res.count shouldEqual 0
      }
      Get("/api/scenes/?bbox=0,0,1000,1000") ~> sceneRoutes ~> check {
        val res = responseAs[PaginatedResponse[SceneWithRelated]]
        res.count shouldEqual 1
      }
    }

    "filter scenes by point" in {
      Get("/api/scenes/?point=101,101") ~> sceneRoutes ~> check {
        val res = responseAs[PaginatedResponse[SceneWithRelated]]
        res.count shouldEqual 1
      }
      Get("/api/scenes/?point=1,1") ~> sceneRoutes ~> check {
        val res = responseAs[PaginatedResponse[SceneWithRelated]]
        res.count shouldEqual 0
      }
    }

    "sort by one field correctly" in {
      val url = s"$baseScene?sort=datasource,desc"
      Get(url) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[SceneWithRelated]].count shouldEqual 2
        responseAs[PaginatedResponse[SceneWithRelated]].results.head.datasource shouldEqual "TEST_ORG-OTHER"
      }
    }

    "sort by two fields correctly" in {
      val url = s"$baseScene?sort=cloudCover,asc;datasource,desc"
      Get(url) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[SceneWithRelated]].count shouldEqual 2
        responseAs[PaginatedResponse[SceneWithRelated]].results.head.datasource shouldEqual "TEST_ORG-OTHER"
      }
    }
  }
}
