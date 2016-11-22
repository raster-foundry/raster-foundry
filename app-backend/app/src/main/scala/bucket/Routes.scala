package com.azavea.rf.bucket

import java.util.UUID

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.auth.Authentication
import com.azavea.rf.database.tables.Buckets
import com.azavea.rf.database.Database
import com.azavea.rf.datamodel._
import com.azavea.rf.scene._
import com.azavea.rf.utils.queryparams.QueryParametersCommon
import com.azavea.rf.utils.UserErrorHandler


trait BucketRoutes extends Authentication
    with QueryParametersCommon
    with SceneQueryParameterDirective
    with PaginationDirectives
    with UserErrorHandler {

  implicit def database: Database

  implicit val rawIntFromEntityUnmarshaller: FromEntityUnmarshaller[UUID] =
    PredefinedFromEntityUnmarshallers.stringUnmarshaller.map{ s =>
      UUID.fromString(s)
    }

  val BULK_OPERATION_MAX_LIMIT = 100

  val bucketRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listBuckets } ~
      post { createBucket }
    } ~
    pathPrefix(JavaUUID) { bucketId =>
      pathEndOrSingleSlash {
        get { getBucket(bucketId) } ~
        put { updateBucket(bucketId) } ~
        delete { deleteBucket(bucketId) }
      } ~
      pathPrefix("scenes") {
        pathEndOrSingleSlash {
          get { listBucketScenes(bucketId) } ~
          post { addBucketScenes(bucketId) } ~
          put { updateBucketScenes(bucketId) } ~
          delete { deleteBucketScenes(bucketId) }
        }
      }
    }
  }

  def listBuckets: Route = authenticate { user =>
    (withPagination & bucketQueryParameters) { (page, bucketQueryParameters) =>
      complete {
        Buckets.listBuckets(page, bucketQueryParameters, user)
      }
    }
  }

  def createBucket: Route = authenticate { user =>
    entity(as[Bucket.Create]) { newBucket =>
      onSuccess(Buckets.insertBucket(newBucket.toBucket(user.id))) { bucket =>
        complete(StatusCodes.Created, bucket)
      }
    }
  }

  def getBucket(bucketId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        Buckets.getBucket(bucketId)
      }
    }
  }

  def updateBucket(bucketId: UUID): Route = authenticate { user =>
    entity(as[Bucket]) { updatedBucket =>
      onSuccess(Buckets.updateBucket(updatedBucket, bucketId, user)) {
        case 1 => complete(StatusCodes.NoContent)
        case count => throw new IllegalStateException(
          s"Error updating bucket: update result expected to be 1, was $count"
        )
      }
    }
  }

  def deleteBucket(bucketId: UUID): Route = authenticate { user =>
    onSuccess(Buckets.deleteBucket(bucketId)) {
      case 1 => complete(StatusCodes.NoContent)
      case 0 => complete(StatusCodes.NotFound)
      case count => throw new IllegalStateException(
        s"Error deleting bucket: delete result expected to be 1, was $count"
      )
    }
  }

  def listBucketScenes(bucketId: UUID): Route = authenticate { user =>
    (withPagination & sceneQueryParameters) { (page, sceneParams) =>
      complete {
        Buckets.listBucketScenes(bucketId, page, sceneParams, user)
      }
    }
  }

  def addBucketScenes(bucketId: UUID): Route = authenticate { user =>
    entity(as[Seq[UUID]]) { sceneIds =>
      if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
        complete(StatusCodes.RequestEntityTooLarge)
      }

      complete {
        Buckets.addScenesToBucket(sceneIds, bucketId)
      }
    }
  }

  def updateBucketScenes(bucketId: UUID): Route = authenticate { user =>
    entity(as[Seq[UUID]]) { sceneIds =>
      if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
        complete(StatusCodes.RequestEntityTooLarge)
      }

      complete {
        Buckets.replaceScenesInBucket(sceneIds, bucketId)
      }
    }
  }

  def deleteBucketScenes(bucketId: UUID): Route = authenticate { user =>
    entity(as[Seq[UUID]]) { sceneIds =>
      if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
        complete(StatusCodes.RequestEntityTooLarge)
      }

      onSuccess(Buckets.deleteScenesFromBucket(sceneIds, bucketId)) {
        _ => complete(StatusCodes.NoContent)
      }
    }
  }
}
