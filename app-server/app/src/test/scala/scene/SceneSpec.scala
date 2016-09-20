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
        responseAs[ScenesRow]
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
        responseAs[PaginatedResponse[ScenesRow]]
      }
    }

    val newSceneDatasource1 = CreateScene(
      publicOrgId, 0, PUBLIC, 20.2f, List("Test", "Public", "Low Resolution"), "TEST_ORG",
      Map("instrument type" -> "satellite", "splines reticulated" -> 0):Map[String, Any], None,
      Some(Timestamp.from(Instant.parse("2016-09-19T14:41:58.408544Z"))),
      PROCESSING, PROCESSING, PROCESSING, None, None
    )

    val newSceneDatasource2 = CreateScene(
      publicOrgId, 0, PUBLIC, 20.2f, List("Test", "Public", "Low Resolution"),
      "TEST_ORG-OTHER", Map("instrument type" -> "satellite", "splines reticulated" -> 0):Map[String, Any],
      None, None, PROCESSING, PROCESSING, PROCESSING, None, None
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
        responseAs[ScenesRow]
      }

      Post("/api/scenes/").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newSceneDatasource2.toJson(createSceneFormat).toString()
        )
      ) ~> sceneRoutes ~> check {
        responseAs[ScenesRow]
      }
    }

    "filter by one organization correctly" in {
      Get(s"$baseScene?organization=${publicOrgId}") ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[ScenesRow]].count shouldEqual 2
      }
    }

    "filter by two organizations correctly" in {
      val url = s"$baseScene?organization=${publicOrgId}&organization=dfac6307-b5ef-43f7-beda-b9f208bb7725"
      Get(url) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[ScenesRow]].count shouldEqual 2
      }
    }

    "filter by one (non-existent) organizations correctly" in {
      val url = s"$baseScene?organization=dfac6307-b5ef-43f7-beda-b9f208bb7725"
      Get(url) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[ScenesRow]].count shouldEqual 0
      }
    }

    "filter by acquisition date correctly (no nulls returned)" in {
      val url = s"$baseScene?minAcquisitionDatetime=2016-09-18T14:41:58.408544z"
      Get(url) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[ScenesRow]].count shouldEqual 1
      }
    }

    "filter by months correctly" in {
      val urlCorrectMonth = s"$baseScene?month=9"
      Get(urlCorrectMonth) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[ScenesRow]].count shouldEqual 1
      }
      val urlMissingMonth = s"$baseScene?month=10"
      Get(urlMissingMonth) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[ScenesRow]].count shouldEqual 0
      }
    }

    "filter by one datasource correctly" in {
      val url = s"$baseScene?datasource=TEST_ORG"
      Get(url) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[ScenesRow]].count shouldEqual 1
      }
    }

    "filter by multiple datasources correctly" in {
      val url = s"$baseScene?datasource=TEST_ORG&datasource=TEST_ORG-OTHER"
      Get(url) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[ScenesRow]].count shouldEqual 2
      }
    }

    "sort by one field correctly" in {
      val url = s"$baseScene?sort=datasource,desc"
      Get(url) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[ScenesRow]].count shouldEqual 2
        responseAs[PaginatedResponse[ScenesRow]].results.head.datasource shouldEqual "TEST_ORG-OTHER"
      }
    }

    "sort by two fields correctly" in {
      val url = s"$baseScene?sort=cloudCover,asc;datasource,desc"
      Get(url) ~> sceneRoutes ~> check {
        responseAs[PaginatedResponse[ScenesRow]].count shouldEqual 2
        responseAs[PaginatedResponse[ScenesRow]].results.head.datasource shouldEqual "TEST_ORG-OTHER"
      }
    }
  }
}
