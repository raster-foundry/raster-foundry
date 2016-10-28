package com.azavea.rf.bucket

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import com.azavea.rf.datamodel._
import concurrent.duration._
import org.scalatest.{Matchers, WordSpec}
import spray.json._

import com.azavea.rf.scene._
import com.azavea.rf.utils.Config
import com.azavea.rf.{DBSpec, Router}


/** Tests to exercise adding/deleting scenes to/from a bucket */
class BucketSceneSpec extends WordSpec
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


  "/api/buckets/{bucket}/scenes/" should {
    "allow creating buckets and scenes" in {
      Post("/api/buckets/").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newBucket1.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        responseAs[Bucket]
      }

      Post("/api/scenes/").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newScene.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        responseAs[Scene.WithRelated]
      }
    }

    "not have any scenes attached to initial bucket" in {
      Get("/api/buckets/") ~> baseRoutes ~> check {
        val buckets = responseAs[PaginatedResponse[Bucket]]
        val bucketId = buckets.results.head.id
        Get(s"/api/buckets/${bucketId}/scenes/") ~> baseRoutes ~> check {
          responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 0
        }
      }
    }

    "should be able to attach scene to bucket via post" in {
      // Get buckets to get ID
      Get("/api/buckets/") ~> baseRoutes ~> check {
        val buckets = responseAs[PaginatedResponse[Bucket]]
        val bucketId = buckets.results.head.id

        // Get scenes to get ID
        Get("/api/scenes/") ~> baseRoutes ~> check {
          val scenes = responseAs[PaginatedResponse[Scene.WithRelated]]
          val sceneId = scenes.results.head.id

          Post(s"/api/buckets/${bucketId}/scenes/${sceneId}/").withHeaders(
            List(authorization)
          ) ~> baseRoutes ~> check {
            status shouldEqual StatusCodes.Created
          }
        }
      }
    }

    "should have one scene attached to bucket" in {
      Get("/api/buckets/") ~> baseRoutes ~> check {
        val buckets = responseAs[PaginatedResponse[Bucket]]
        val bucketId = buckets.results.head.id
        Get(s"/api/buckets/${bucketId}/scenes/") ~> baseRoutes ~> check {
          responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 1
        }
      }
    }

    "should be able to apply filters for scenes on bucket" in {
      Get("/api/buckets/") ~> baseRoutes ~> check {
        val buckets = responseAs[PaginatedResponse[Bucket]]
        val bucketId = buckets.results.head.id
        Get(s"/api/buckets/${bucketId}/scenes/?datasource=DoesNotExist") ~> baseRoutes ~> check {
          responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 0
        }
      }
    }

    "should be able to remove scene from bucket via delete" in {
      Get("/api/buckets/") ~> baseRoutes ~> check {
        val buckets = responseAs[PaginatedResponse[Bucket]]
        val bucketId = buckets.results.head.id

        // Get scenes to get ID
        Get("/api/scenes/") ~> baseRoutes ~> check {
          val scenes = responseAs[PaginatedResponse[Scene.WithRelated]]
          val sceneId = scenes.results.head.id

          Delete(s"/api/buckets/${bucketId}/scenes/${sceneId}/").withHeaders(
            List(authorization)
          ) ~> baseRoutes ~> check {
            status shouldEqual StatusCodes.NoContent
          }
        }
      }
    }

    "should not have a scene attached to bucket after delete" in {
      Get("/api/buckets/") ~> baseRoutes ~> check {
        val buckets = responseAs[PaginatedResponse[Bucket]]
        val bucketId = buckets.results.head.id
        Get(s"/api/buckets/${bucketId}/scenes/") ~> baseRoutes ~> check {
          responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 0
        }
      }
    }
  }
}
