package com.azavea.rf.api.datasource

import java.util.UUID

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.http.scaladsl.server.Route
import akka.actor.ActorSystem
import concurrent.duration._

import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.Config
import com.azavea.rf.api._
import com.azavea.rf.common._
import java.sql.Timestamp
import java.time.Instant

import io.circe._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

class DatasourceSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {
  implicit val ec = system.dispatcher

  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(5).second)

  val authHeader = AuthUtils.generateAuthHeader("Default")
  val baseDatasourcePath = "/api/datasources/"
  val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")

  val datasource1 = Datasource.Create(
    publicOrgId,
    "Datasource1",
    Visibility.Public,
    None: Option[String],
    ().asJson,
    ().asJson
  )

  val datasource2 = Datasource.Create(
    publicOrgId,
    "Datasource2",
    Visibility.Public,
    None: Option[String],
    ().asJson,
    ().asJson
  )

  // A third datasource scoped to a different privacy level
  val datasource3 = Datasource.Create(
    publicOrgId,
    "Datasource3",
    Visibility.Private,
    None: Option[String],
    ().asJson,
    ().asJson
  )

  // Alias to baseRoutes to be explicit
  val baseRoutes = routes

  "/api/datasources/" should {
    "require authentication" in {
      Get("/api/datasources/") ~> baseRoutes ~> check {
        reject
      }
      Get("/api/datasources/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Datasource]]
      }
    }

    "create an datasource successfully once authenticated" in {
      Post("/api/datasources/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          datasource1.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        val ds = responseAs[Datasource]
        ds.owner shouldEqual "Default"
      }

      Post("/api/datasources/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          datasource2.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        responseAs[Datasource]
      }

      Post("/api/datasources/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          datasource3.asJson.noSpaces
        )
      ) ~> baseRoutes ~> check {
        responseAs[Datasource]
      }
    }
  }

  /** Ignored because there's only one user + org in the db with datasources */
  "scope to organization and privacy correctly" ignore {
    Get(s"$baseDatasourcePath?organization=${publicOrgId}").withHeaders(
      List(authHeader)
    ) ~> baseRoutes ~> check {
      // total is 4 because of landsat and sentinel in migration 38
      responseAs[PaginatedResponse[Datasource]].count shouldEqual 3
    }
  }

  "filter by name correctly" in {
    val url1 = s"$baseDatasourcePath?name=${datasource1.name}"
    Get(url1).withHeaders(
      List(authHeader)
    ) ~> baseRoutes ~> check {
      responseAs[PaginatedResponse[Datasource]].count shouldEqual 1
    }

    val url2 = s"$baseDatasourcePath?name=${datasource2.name}"
    Get(url2).withHeaders(
      List(authHeader)
    ) ~> baseRoutes ~> check {
      responseAs[PaginatedResponse[Datasource]].count shouldEqual 1
    }

    val url3 = s"$baseDatasourcePath?name=${datasource3.name}"
    Get(url3).withHeaders(
      List(authHeader)
    ) ~> baseRoutes ~> check {
      responseAs[PaginatedResponse[Datasource]].count shouldEqual 1
    }
  }
}
