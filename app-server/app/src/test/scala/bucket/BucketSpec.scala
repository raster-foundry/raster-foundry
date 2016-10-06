package com.azavea.rf.bucket

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.actor.ActorSystem
import concurrent.duration._
import spray.json._

import com.azavea.rf.datamodel._
import com.azavea.rf.utils.Config
import com.azavea.rf.{DBSpec, Router}


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

  "/api/buckets/{uuid}" should {

    "return a 404 for non-existent bucket" in {
      Get(s"${baseBucket}${publicOrgId}") ~> bucketRoutes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return a bucket" ignore {
      val bucketId = ""
      Get(s"${baseBucket}${bucketId}/") ~> bucketRoutes ~> check {
        responseAs[Bucket]
      }
    }

    "update a bucket" ignore {
      // Add change to bucket here
    }

    "delete a bucket" ignore {
      val bucketId = ""
      Delete(s"${baseBucket}${bucketId}/") ~> bucketRoutes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
  }

  "/api/buckets/" should {
    "not require authentication" in {
      Get("/api/buckets/") ~> bucketRoutes ~> check {
        responseAs[PaginatedResponse[Bucket]]
      }
    }

    "require authentication for creation" in {
      Post("/api/buckets/").withEntity(
        HttpEntity(
          ContentTypes.`application/json`,
          newBucket1.toJson(createBucketFormat).toString()
        )
      ) ~> bucketRoutes ~> check {
        reject
      }
    }

    "create a bucket successfully once authenticated" in {
      Post("/api/buckets/").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newBucket1.toJson(createBucketFormat).toString()
        )
      ) ~> bucketRoutes ~> check {
        responseAs[Bucket]
      }

      Post("/api/buckets/").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newBucket2.toJson(createBucketFormat).toString()
        )
      ) ~> bucketRoutes ~> check {
        responseAs[Bucket]
      }
    }

    "filter by one organization correctly" in {
      Get(s"/api/buckets/?organization=${publicOrgId}") ~> bucketRoutes ~> check {
        responseAs[PaginatedResponse[Bucket]].count shouldEqual 2
      }
    }

    "filter by two organizations correctly" in {
      val url = s"/api/buckets/?organization=${publicOrgId}&organization=${fakeOrgId}"
      Get(url) ~> bucketRoutes ~> check {
        responseAs[PaginatedResponse[Bucket]].count shouldEqual 2
      }
    }

    "filter by one (non-existent) organizations correctly" in {
      val url = s"/api/buckets/?organization=${fakeOrgId}"
      Get(url) ~> bucketRoutes ~> check {
        responseAs[PaginatedResponse[Bucket]].count shouldEqual 0
      }
    }

    "filter by created by real user correctly" in {
      val url = s"/api/buckets/?createdBy=Default"
      Get(url) ~> bucketRoutes ~> check {
        responseAs[PaginatedResponse[Bucket]].count shouldEqual 2
      }
    }

    "filter by created by fake user correctly" in {
      val url = s"/api/buckets/?createdBy=IsNotReal"
      Get(url) ~> bucketRoutes ~> check {
        responseAs[PaginatedResponse[Bucket]].count shouldEqual 0
      }
    }

    "sort by one field correctly" in {
      val url = s"/api/buckets/?sort=name,desc"
      Get(url) ~> bucketRoutes ~> check {
        responseAs[PaginatedResponse[Bucket]].count shouldEqual 2
        responseAs[PaginatedResponse[Bucket]].results.head.name shouldEqual "Test Two"
      }
    }

    "sort by two fields correctly" in {
      val url = s"/api/buckets/?sort=visibility,asc;name,desc"
      Get(url) ~> bucketRoutes ~> check {
        responseAs[PaginatedResponse[Bucket]].count shouldEqual 2
        responseAs[PaginatedResponse[Bucket]].results.head.name shouldEqual "Test Two"
      }
    }
  }
}
