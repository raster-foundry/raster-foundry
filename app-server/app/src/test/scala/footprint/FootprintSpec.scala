package com.azavea.rf.footprint

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.actor.ActorSystem
import concurrent.duration._
import spray.json._
import java.util.UUID
import java.sql.Timestamp
import java.time.Instant

import geotrellis.vector.io._
import geotrellis.vector.{MultiPolygon, Polygon, Point, Geometry}
import geotrellis.slick.Projected

import com.azavea.rf.utils.Config
import com.azavea.rf.{DBSpec, Router}
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.datamodel.enums._
import com.azavea.rf.utils.PaginatedResponse
import com.azavea.rf.AuthUtils
import com.azavea.rf.scene._

class FootprintSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {
  implicit val ec = system.dispatcher
  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(20).second)

  val authorization = AuthUtils.generateAuthHeader("User")
  val baseScene = "/api/scenes/"
  val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")

  "/api/footprints" should {
    "return a paginated list of footprints" in {
      Get("/api/footprints") ~> footprintRoutes ~> check {
        responseAs[PaginatedResponse[FootprintWithGeojson]]
      }
    }
    "require authentication to create new footprints" in {
      Post("/api/footprints")
        .withEntity(
        HttpEntity(ContentTypes.`application/json`, "")
      ) ~> footprintRoutes ~> check {
        rejections.length shouldEqual 2
      }
    }
    "allow the creation of new footprints" in {
      val newSceneDatasource = CreateScene(
        publicOrgId, 0, PUBLIC, 20.2f, List("Test", "Public", "Low Resolution"), "TEST_ORG",
        Map("instrument type" -> "satellite", "splines reticulated" -> 0):Map[String, Any], None,
        Some(Timestamp.from(Instant.parse("2016-09-19T14:41:58.408544Z"))),
        PROCESSING, PROCESSING, PROCESSING, None, None, "test scene datasource",
        List(): List[SceneImage], None, List(): List[SceneThumbnail]
      )
      Post("/api/scenes/").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newSceneDatasource.toJson(createSceneFormat).toString()
        )
      ) ~> sceneRoutes ~> check {
        val scene = responseAs[SceneWithRelated]
        val poly = Projected(
          MultiPolygon(Polygon(Seq(Point(100,100), Point(110,100), Point(110,110),
                                   Point(100,110), Point(100,100)))), 3857)
        val newFootprint = FootprintWithGeojsonCreate(
          publicOrgId,
          poly.geom.toGeoJson.parseJson.asJsObject,
          scene.id
          )
        Post("/api/footprints").withHeadersAndEntity(
          List(authorization),
          HttpEntity(
            ContentTypes.`application/json`,
            newFootprint.toJson(footprintWithGeojsonCreateFormat).toString()
          )
        ) ~> footprintRoutes ~> check {
          responseAs[FootprintWithGeojson]
        }
      }
    }
    "allow updating a footprint" ignore {
      // Add change to footprint here
    }
    "allow deleting a footprint" ignore {
      // Add deletion of footprint here
    }
    "filter footprints by point" in {
      Get("/api/footprints/?x=101&y=101") ~> footprintRoutes ~> check {
        val res = responseAs[PaginatedResponse[FootprintWithGeojson]]
        res.count shouldEqual 1
      }
      Get("/api/footprints/?x=1&y=1") ~> footprintRoutes ~> check {
        val res = responseAs[PaginatedResponse[FootprintWithGeojson]]
        res.count shouldEqual 0
      }
    }
    "filter footprints by bounding box" in {
      Get("/api/footprints/?bbox=0,0,1,1") ~> footprintRoutes ~> check {
        val res = responseAs[PaginatedResponse[FootprintWithGeojson]]
        res.count shouldEqual 0
      }
      Get("/api/footprints/?bbox=0,0,1000,1000") ~> footprintRoutes ~> check {
        val res = responseAs[PaginatedResponse[FootprintWithGeojson]]
        res.count shouldEqual 1
      }
    }
    "filter footprints by scene" in {
      Get(s"/api/scenes/?organization=$publicOrgId") ~> sceneRoutes ~> check {
        val res = responseAs[PaginatedResponse[SceneWithRelated]]
        Get(s"/api/footprints/?scene=${res.results(0).id}") ~> footprintRoutes ~> check {
          val res = responseAs[PaginatedResponse[FootprintWithGeojson]]
          res.count === 1
        }
        // check for non-existent uuid
        Get(s"/api/footprints/?scene=0905f06b-ff80-45cf-b655-3fb95c7eb7af") ~>
          footprintRoutes ~> check {
          val res = responseAs[PaginatedResponse[FootprintWithGeojson]]
          res.count === 0
        }
      }
    }
  }
}
