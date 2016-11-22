package com.azavea.rf.bucket

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.http.scaladsl.server.Route
import akka.actor.ActorSystem
import concurrent.duration._
import spray.json._

import com.azavea.rf.datamodel._
import com.azavea.rf.utils.Config
import com.azavea.rf.{DBSpec, Router, AuthUtils}


class BucketSpec extends WordSpec
    with BucketSpecHelper
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {
  implicit val ec = system.dispatcher

  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(20).second)

  // Alias to baseRoutes to be explicit
  val baseRoutes = routes
  val authHeader = AuthUtils.generateAuthHeader("Default")

  "/api/buckets/{uuid}" should {

    "return a 404 for non-existent bucket" in {
      Get(s"${baseBucket}${publicOrgId}").withHeaders(
        List(authHeader)
      ) ~> Route.seal(baseRoutes) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return a bucket" ignore {
      val bucketId = ""
      Get(s"${baseBucket}${bucketId}/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[Bucket]
      }
    }

    "update a bucket" ignore {
      // Add change to bucket here
    }

    "delete a bucket with authentication" ignore {
      val bucketId = ""
      Delete(s"${baseBucket}${bucketId}/") ~> baseRoutes ~> check {
        reject
      }
      Delete(s"${baseBucket}${bucketId}/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
  }

  "/api/buckets/" should {
    "require authentication" in {
      Get("/api/buckets/") ~> baseRoutes ~> check {
        reject
      }
      Get("/api/buckets/").withHeaders(
        List(authHeader)
      )  ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Bucket]]
      }
    }

    "require authentication for creation" in {
      Post("/api/buckets/").withEntity(
        HttpEntity(
          ContentTypes.`application/json`,
          newBucket1.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        reject
      }
    }

    "create a bucket successfully once authenticated" in {
      Post("/api/buckets/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newBucket1.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        responseAs[Bucket]
      }

      Post("/api/buckets/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newBucket2.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        responseAs[Bucket]
      }
    }

    // TODO: https://github.com/azavea/raster-foundry/issues/712
    "filter by one organization correctly" ignore {
      Get(s"/api/buckets/?organization=${publicOrgId}").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Bucket]].count shouldEqual 2
      }
    }

    // TODO: https://github.com/azavea/raster-foundry/issues/712
    "filter by two organizations correctly" ignore {
      val url = s"/api/buckets/?organization=${publicOrgId}&organization=${fakeOrgId}"
      Get(url).withHeaders(
        List(authHeader)
      )  ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Bucket]].count shouldEqual 2
      }
    }

    "filter by one (non-existent) organizations correctly" in {
      val url = s"/api/buckets/?organization=${fakeOrgId}"
      Get(url).withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Bucket]].count shouldEqual 0
      }
    }


    // TODO: https://github.com/azavea/raster-foundry/issues/712
    "filter by created by real user correctly" ignore {
      val url = s"/api/buckets/?createdBy=Default"
      Get(url).withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Bucket]].count shouldEqual 2
      }
    }

    "filter by created by fake user correctly" in {
      val url = s"/api/buckets/?createdBy=IsNotReal"
      Get(url).withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Bucket]].count shouldEqual 0
      }
    }

    "sort by one field correctly" in {
      val url = s"/api/buckets/?sort=name,desc"
      Get(url).withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Bucket]].count shouldEqual 2
        responseAs[PaginatedResponse[Bucket]].results.head.name shouldEqual "Test Two"
      }
    }

    "sort by two fields correctly" in {
      val url = s"/api/buckets/?sort=visibility,asc;name,desc"
      Get(url).withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Bucket]].count shouldEqual 2
        responseAs[PaginatedResponse[Bucket]].results.head.name shouldEqual "Test Two"
      }
    }
  }
}