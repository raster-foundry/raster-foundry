package com.azavea.rf.bucket

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.scene._
import com.azavea.rf.auth.Authentication
import com.azavea.rf.datamodel._
import com.azavea.rf.database.tables._
import com.azavea.rf.database.Database
import com.azavea.rf.utils.queryparams.QueryParametersCommon
import com.azavea.rf.scene.SceneQueryParameterDirective


trait BucketRoutes extends Authentication
    with QueryParametersCommon
    with SceneQueryParameterDirective
    with PaginationDirectives {

  implicit def database: Database
  implicit val ec: ExecutionContext

  implicit val rawIntFromEntityUnmarshaller: FromEntityUnmarshaller[UUID] =
    PredefinedFromEntityUnmarshallers.stringUnmarshaller.map{ s =>
      UUID.fromString(s)
    }

  def bucketRoutes: Route = {
    pathPrefix("api" / "buckets") {
      pathEndOrSingleSlash {
        authenticateAndAllowAnonymous { user =>
          withPagination { page =>
            get {
              bucketQueryParameters { bucketQueryParameters =>
                onSuccess(Buckets.getBuckets(page, bucketQueryParameters)) { scenes =>
                  complete(scenes)
                }
              }
            }
          }
        } ~
        authenticate { user =>
          post {
            entity(as[Bucket.Create]) { newBucket =>
              onComplete(Buckets.insertBucket(newBucket.toBucket(user.id))) {
                case Success(bucket) => complete(bucket)
                case Failure(_) => complete(StatusCodes.InternalServerError)
              }
            }
          }
        }
      } ~
      pathPrefix(JavaUUID) { bucketId =>
        pathEndOrSingleSlash {
          authenticateAndAllowAnonymous { user =>
            get {
              onSuccess(Buckets.getBucket(bucketId)) {
                case Some(bucket) => complete(bucket)
                case _ => complete(StatusCodes.NotFound)
              }
            }
          } ~
          authenticate { user =>
            put {
              entity(as[Bucket]) { updatedBucket =>
                onSuccess(Buckets.updateBucket(updatedBucket, bucketId, user)) {
                  case 1 => complete(StatusCodes.NoContent)
                }
              }
            } ~
            delete {
              onSuccess(Buckets.deleteBucket(bucketId)) {
                case 1 => complete(StatusCodes.NoContent)
                case 0 => complete(StatusCodes.NotFound)
                case _ => complete(StatusCodes.InternalServerError)
              }
            }
          }
        } ~
        pathPrefix("scenes") {
          pathEndOrSingleSlash {
            authenticateAndAllowAnonymous { user =>
              withPagination { page =>
                get {
                  sceneQueryParameters { sceneParams =>
                    onSuccess(Buckets.getBucketScenes(bucketId, page, sceneParams)) { scenes =>
                      complete(scenes)
                    }
                  }
                }
              }
            }
          } ~
          authenticate { user =>
            pathPrefix(JavaUUID) { sceneId =>
              pathEndOrSingleSlash {
                post {
                  onSuccess(Buckets.addSceneToBucket(sceneId, bucketId)) { resp =>
                    complete(StatusCodes.Created)
                  }
                } ~
                delete {
                  onSuccess(Buckets.deleteSceneFromBucket(sceneId, bucketId)) {
                    case 1 => complete(StatusCodes.NoContent)
                    case 0 => complete(StatusCodes.NotFound)
                    case _ => complete(StatusCodes.InternalServerError)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

object BucketRoutes {

}
