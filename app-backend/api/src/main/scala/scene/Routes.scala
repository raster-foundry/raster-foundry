package com.azavea.rf.api.scene

import com.azavea.rf.api.utils.Config
import com.azavea.rf.authentication.Authentication
import com.azavea.rf.common.{AWSBatch, CommonHandlers, S3, UserErrorHandler}
import com.azavea.rf.datamodel._
import akka.http.scaladsl.model.{HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.util.ByteString
import com.amazonaws.services.s3.AmazonS3URI
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import geotrellis.raster.MultibandTile
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import kamon.akka.http.KamonTraceDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import java.net._
import java.util.UUID
import org.apache.commons.codec.binary.{Base64 => ApacheBase64}

import cats.effect.IO
import com.azavea.rf.database._
import doobie.util.transactor.Transactor
import com.azavea.rf.datamodel._
import com.azavea.rf.database.filter.Filterables._
import cats.implicits._
import com.azavea.rf.common.utils.CogUtils
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._

import com.lonelyplanet.akka.http.extensions.PageRequest
import com.azavea.rf.database.util.Page

import scala.concurrent.Future

trait SceneRoutes extends Authentication
    with Config
    with SceneQueryParameterDirective
    with ThumbnailQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with AWSBatch
    with UserErrorHandler
    with KamonTraceDirectives {

  val xa: Transactor[IO]

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
      } ~
      pathPrefix("permissions") {
        pathEndOrSingleSlash {
          put {
            traceName("replace-scene-permissions") {
              replaceScenePermissions(sceneId)
            }
          }
        } ~
        post {
          traceName("add-scene-permission") {
            addScenePermission(sceneId)
          }
        } ~
        get {
          traceName("list-scene-permissions") {
            listScenePermissions(sceneId)
          }
        } ~
        delete {
          deleteScenePermissions(sceneId)
        }
      } ~
      pathPrefix("actions") {
        pathEndOrSingleSlash {
          get {
            traceName("list-user-allowed-actions") {
              listUserSceneActions(sceneId)
            }
          }
        }
      } ~
      pathPrefix("datasource") {
        pathEndOrSingleSlash { getSceneDatasource(sceneId) }
      } ~
      pathPrefix("thumbnail") {
        pathEndOrSingleSlash { getSceneThumbnail(sceneId) }
      }
    }
  }

  def listScenes: Route = authenticate { user =>
    (withPagination & sceneQueryParameters) { (page, sceneParams) =>
      complete {
        SceneWithRelatedDao.listAuthorizedScenes(page, sceneParams, user).transact(xa).unsafeToFuture
      }
    }
  }

  def createScene: Route = authenticate { user =>
    entity(as[Scene.Create]) { newScene =>

      val tileFootprint = (newScene.sceneType, newScene.ingestLocation, newScene.tileFootprint) match {
        case (Some(SceneType.COG), Some(ingestLocation), None) => {
          logger.info(s"Generating Footprint for Newly Added COG")
          CogUtils.getTiffExtent(ingestLocation)
        }
        case (_, _, tf@Some(_)) => {
          logger.info("Not generating footprint, already exists")
          tf
        }
        case _ => None
      }

      val dataFootprint = (tileFootprint, newScene.dataFootprint) match {
        case (Some(tf), None) => tileFootprint
        case _ => newScene.dataFootprint
      }

      val updatedScene = newScene.copy(dataFootprint = dataFootprint, tileFootprint = tileFootprint)

      onSuccess(SceneDao.insert(updatedScene, user).transact(xa).unsafeToFuture) { scene =>
        if (scene.statusFields.ingestStatus == IngestStatus.ToBeIngested) kickoffSceneIngest(scene.id)
        complete((StatusCodes.Created, scene))
      }
    }
  }

  def getScene(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao.authViewQuery(user, ObjectType.Scene)
        .filter(sceneId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      rejectEmptyResponse {
        complete {
          SceneWithRelatedDao.getScene(sceneId, user).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def updateScene(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao.query
        .authorized(user, ObjectType.Scene, sceneId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[Scene]) { updatedScene =>
        onSuccess(SceneDao.update(updatedScene, sceneId, user).transact(xa).unsafeToFuture) { case (result, kickoffIngest) =>
          if (kickoffIngest) kickoffSceneIngest(sceneId)
          completeSingleOrNotFound(result)
        }
      }
    }
  }

  def deleteScene(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao.query
        .authorized(user, ObjectType.Scene, sceneId, ActionType.Delete)
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(SceneDao.query.filter(sceneId).delete.transact(xa).unsafeToFuture) {
        completeSingleOrNotFound
      }
    }
  }

  def getDownloadUrl(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneWithRelatedDao.query
        .authorized(user, ObjectType.Scene, sceneId, ActionType.Download)
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(SceneWithRelatedDao.getScene(sceneId, user).transact(xa).unsafeToFuture) { scene =>
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

  def listScenePermissions(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao.query.ownedBy(user, sceneId).exists.transact(xa).unsafeToFuture
    } {
      complete {
        AccessControlRuleDao.listByObject(ObjectType.Scene, sceneId).transact(xa).unsafeToFuture
      }
    }
  }

  def replaceScenePermissions(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao.query.ownedBy(user, sceneId).exists.transact(xa).unsafeToFuture
    } {
      entity(as[List[AccessControlRule.Create]]) { acrCreates =>
        complete {
          AccessControlRuleDao.replaceWithResults(
            user, ObjectType.Scene, sceneId, acrCreates
          ).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def addScenePermission(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao.query.ownedBy(user, sceneId).exists.transact(xa).unsafeToFuture
    } {
      entity(as[AccessControlRule.Create]) { acrCreate =>
        complete {
          AccessControlRuleDao.createWithResults(
            acrCreate.toAccessControlRule(user, ObjectType.Scene, sceneId)
          ).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def listUserSceneActions(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao.authViewQuery(user, ObjectType.Scene)
        .filter(sceneId)
        .exists
        .transact(xa).unsafeToFuture
    } { user.isSuperuser match {
      case true => complete(List("*"))
      case false =>
        onSuccess(
          SceneWithRelatedDao.unsafeGetScene(sceneId, user).transact(xa).unsafeToFuture
      ) { scene =>
        scene.owner == user.id match {
          case true => complete(List("*"))
          case false => complete {
            AccessControlRuleDao.listUserActions(user, ObjectType.Scene, sceneId)
              .transact(xa).unsafeToFuture
            }
          }
        }
      }
    }
  }

  def deleteScenePermissions(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao.query.ownedBy(user, sceneId).exists.transact(xa).unsafeToFuture
    } {
      complete {
        AccessControlRuleDao.deleteByObject(ObjectType.Scene, sceneId).transact(xa).unsafeToFuture
      }
    }
  }

  def getSceneDatasource(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao.authViewQuery(user, ObjectType.Scene)
        .filter(sceneId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(SceneDao.getSceneDatasource(sceneId).transact(xa).unsafeToFuture) { datasourceO =>
        complete { datasourceO }
      }
    }
  }

  def getSceneThumbnail(sceneId: UUID): Route = authenticateWithParameter { user =>
    {
      thumbnailQueryParameters {
        thumbnailParams => {
          authorizeAsync {
            SceneDao.authViewQuery(user, ObjectType.Scene)
              .filter(sceneId)
              .exists
              .transact(xa)
              .unsafeToFuture
          } {
            complete {
              SceneDao.unsafeGetSceneById(sceneId).transact(xa).unsafeToFuture >>=
                { (scene: Scene) =>
                  (scene.ingestLocation, scene.sceneType) match {
                    case (Some(uri), Some(SceneType.COG)) => {
                      CogUtils.thumbnail(
                        uri, thumbnailParams.width, thumbnailParams.height
                      ).map(
                        (tile: MultibandTile) => HttpEntity(MediaTypes.`image/png`, tile.renderPng.bytes)).value
                    }
                    case _ =>
                      Future.successful(None)
                  }
                }
            }
          }
        }
      }
    }
  }
}
