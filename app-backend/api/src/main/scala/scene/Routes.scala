package com.azavea.rf.api.scene

import com.azavea.rf.api.utils.Config
import com.azavea.rf.common.{AWSBatch, Authentication, UserErrorHandler, CommonHandlers, S3}
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Scenes
import com.azavea.rf.datamodel._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.amazonaws.services.s3.AmazonS3URI
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import kamon.akka.http.KamonTraceDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import java.net._
import java.util.UUID


trait SceneRoutes extends Authentication
    with Config
    with SceneQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with AWSBatch
    with UserErrorHandler
    with KamonTraceDirectives {

  implicit def database: Database

  val sceneRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get {
        traceName("scenes-list") {
        listScenes }
      } ~
      post {
        traceName("scenes-create") {
        createScene }
      }
    } ~
    pathPrefix(JavaUUID) { sceneId =>
      pathEndOrSingleSlash {
        get {
          traceName("scenes-detail") {
          getScene(sceneId)
          }
        } ~
        put { traceName("scenes-update") {
          updateScene(sceneId) }
        } ~
        delete { traceName("scenes-delete") {
          deleteScene(sceneId) }
        }
      } ~
      pathPrefix("download") {
        pathEndOrSingleSlash {
          get {
            traceName("scene-download-url") {
              getDownloadUrl(sceneId)
            }
          }
        }
      }
    }
  }

  def listScenes: Route = authenticate { user =>
    (withPagination & sceneQueryParameters) { (page, sceneParams) =>
      complete {
        Scenes.listScenes(page, sceneParams, user)
      }
    }
  }

  def createScene: Route = authenticate { user =>
    entity(as[Scene.Create]) { newScene =>
      authorize(user.isInRootOrSameOrganizationAs(newScene)) {
        onSuccess(Scenes.insertScene(newScene, user)) { scene =>
          if (scene.statusFields.ingestStatus == IngestStatus.ToBeIngested) kickoffSceneIngest(scene.id)
          complete((StatusCodes.Created, scene))
        }
      }
    }
  }

  def getScene(sceneId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        Scenes.getScene(sceneId, user)
      }
    }
  }

  def updateScene(sceneId: UUID): Route = authenticate { user =>
    entity(as[Scene]) { updatedScene =>
      authorize(user.isInRootOrSameOrganizationAs(updatedScene)) {
        onSuccess(Scenes.updateScene(updatedScene, sceneId, user)) { (result, kickoffIngest) =>
          if (kickoffIngest) kickoffSceneIngest(sceneId)
          completeSingleOrNotFound(result)
        }
      }
    }
  }

  def deleteScene(sceneId: UUID): Route = authenticate { user =>
    onSuccess(Scenes.deleteScene(sceneId, user)) {
      completeSingleOrNotFound
    }
  }

  def getDownloadUrl(sceneId: UUID): Route = authenticate { user =>
    onSuccess(Scenes.getScene(sceneId, user)) { scene =>
      complete {
        scene.getOrElse {
          throw new Exception("Scene does not exist or is not accessible by this user")
        }.images map { image =>
          val downloadUri: String = {
            image.sourceUri match {
              case uri if uri.startsWith(s"s3://$dataBucket") => {
                val s3Uri = new AmazonS3URI(image.sourceUri)
                S3.getSignedUrl(s3Uri.getBucket, s3Uri.getKey).toString
              }
              case uri if uri.startsWith("s3://") => {
                val s3Uri = new AmazonS3URI(image.sourceUri)
                s"https://${s3Uri.getBucket}.s3.amazonaws.com/${s3Uri.getKey}"
              }
              case _ => image.sourceUri
            }
          }
          image.toDownloadable(downloadUri)
        }
      }
    }
  }
}
