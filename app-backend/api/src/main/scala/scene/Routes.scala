package com.azavea.rf.api.scene

import com.azavea.rf.api.utils.Config
import com.azavea.rf.common.{AWSBatch, Authentication, CommonHandlers, S3, UserErrorHandler}
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
import scala.util.{Failure, Success}
import java.net._
import java.util.UUID

import cats.effect.IO
import com.azavea.rf.database.{SceneDao, SceneWithRelatedDao}
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

trait SceneRoutes extends Authentication
    with Config
    with SceneQueryParameterDirective
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
      }
    }
  }

  def listAuthorizedScenes(pageRequest: PageRequest, sceneParams: CombinedSceneQueryParams, user: User): ConnectionIO[PaginatedResponse[Scene.WithRelated]] = {
    val pageFragment: Fragment = Page(pageRequest)
    val queryFilters: List[Option[Fragment]] = SceneWithRelatedDao.makeFilters(List(sceneParams)).flatten
    val scenesIO: ConnectionIO[List[Scene]] =
      (SceneWithRelatedDao.selectF ++
        Fragments.whereAndOpt(
          (SceneWithRelatedDao.query.authorizeF(user, ObjectType.Scene, ActionType.View) :: queryFilters): _*) ++
          pageFragment)
        .query[Scene]
        .stream
        .compile
        .toList
    val withRelatedsIO: ConnectionIO[List[Scene.WithRelated]] = scenesIO flatMap { SceneWithRelatedDao.scenesToScenesWithRelated }

    for {
      page <- withRelatedsIO
      count <- SceneWithRelatedDao.query
        .filter(Fragments.andOpt(queryFilters.toSeq: _*))
        .authorize(user, ObjectType.Scene, ActionType.View)
        .countIO
    } yield {
      val hasPrevious = pageRequest.offset > 0
      val hasNext = ((pageRequest.offset + 1) * pageRequest.limit) < count
      PaginatedResponse[Scene.WithRelated](count, hasPrevious, hasNext, pageRequest.offset, pageRequest.limit, page)
    }
  }

  def listScenes: Route = authenticate { user =>
    (withPagination & sceneQueryParameters) { (page, sceneParams) =>
      complete {
        listAuthorizedScenes(page, sceneParams, user).transact(xa).unsafeToFuture
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
        case _ => {
          logger.info("Not generating footprint, already exists")
          None
        }
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
      SceneWithRelatedDao.query
        .authorized(user, ObjectType.Scene, sceneId, ActionType.View)
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
        .authorized(user, ObjectType.Scene, sceneId, ActionType.View)
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
}
