package com.azavea.rf.bucket

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.auth.Authentication
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.utils.Database

import com.azavea.rf.scene._
import com.azavea.rf.scene.SceneQueryParameterDirective


trait BucketRoutes extends Authentication
    with BucketQueryParameterDirective
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
                onSuccess(BucketService.getBuckets(page, bucketQueryParameters)) { scenes =>
                  complete(scenes)
                }
              }
            }
          }
        } ~
        authenticate { user =>
          post {
            entity(as[CreateBucket]) { newBucket =>
              onSuccess(BucketService.insertBucket(newBucket.toBucket(user.id))) {
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
              onSuccess(BucketService.getBucket(bucketId)) {
                case Some(bucket) => complete(bucket)
                case _ => complete(StatusCodes.NotFound)
              }
            }
          } ~
          authenticate { user =>
            put {
              entity(as[BucketsRow]) { updatedBucket =>
                onSuccess(BucketService.updateBucket(updatedBucket, bucketId, user)) {
                  case Success(result) => {
                    result match {
                      case 1 => complete(StatusCodes.NoContent)
                      case count: Int => throw new Exception(
                        s"Error updating bucket: update result expected to be 1, was $count"
                      )
                    }
                  }
                  case Failure(e) => throw e
                }
              }
            } ~
            delete {
              onSuccess(BucketService.deleteBucket(bucketId)) {
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
                    onSuccess(BucketService.getBucketScenes(bucketId, page, sceneParams)) { scenes =>
                      complete(scenes)
                    }
                  }
                }
              }
            }
          } ~
          authenticate { user =>
            pathPrefix(JavaUUID) { sceneId =>
              println(s"UUID: $sceneId")
              pathEndOrSingleSlash {
                post {
                  onSuccess(BucketService.addSceneToBucket(sceneId, bucketId)) { resp =>
                    complete(StatusCodes.Created)
                  }
                } ~
                delete {
                  onSuccess(BucketService.deleteSceneFromBucket(sceneId, bucketId)) {
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
