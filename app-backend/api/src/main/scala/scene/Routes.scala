package com.rasterfoundry.api.scene

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.data._
import cats.effect.IO
import cats.implicits._
import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.common.utils.CogUtils
import com.rasterfoundry.common.{AWSBatch, S3}
import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  UserErrorHandler
}
import com.rasterfoundry.database._
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.datamodel._
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.implicits._
import doobie.util.transactor.Transactor
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.io.json.HistogramJsonFormats
import io.circe.parser._

import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.rasterfoundry.common.LayerAttribute

trait SceneRoutes
    extends Authentication
    with Config
    with SceneQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with AWSBatch
    with UserErrorHandler
    with HistogramJsonFormats {

  val xa: Transactor[IO]

  val sceneRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get {
        listScenes
      } ~
        handleExceptions(cogMissingHandler) {
          post {
            createScene
          }
        }
    } ~
      pathPrefix(JavaUUID) { sceneId =>
        pathEndOrSingleSlash {
          get {
            getScene(sceneId)
          } ~
            put {
              updateScene(sceneId)
            } ~
            delete {
              deleteScene(sceneId)
            }
        } ~
          pathPrefix("download") {
            pathEndOrSingleSlash {
              get {
                getDownloadUrl(sceneId)
              }
            }
          } ~
          pathPrefix("permissions") {
            pathEndOrSingleSlash {
              put {
                replaceScenePermissions(sceneId)
              }
            } ~
              post {
                addScenePermission(sceneId)
              } ~
              get {
                listScenePermissions(sceneId)
              } ~
              delete {
                deleteScenePermissions(sceneId)
              }
          } ~
          pathPrefix("actions") {
            pathEndOrSingleSlash {
              get {
                listUserSceneActions(sceneId)
              }
            }
          } ~
          pathPrefix("datasource") {
            pathEndOrSingleSlash { getSceneDatasource(sceneId) }
          }
      }
  }

  def listScenes: Route = authenticate { user =>
    (withPagination & sceneQueryParameters) { (page, sceneParams) =>
      complete {
        SceneWithRelatedDao
          .listAuthorizedScenes(page, sceneParams, user)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def createScene: Route = authenticate { user =>
    entity(as[Scene.Create]) { newScene =>
      val tileFootprint = (newScene.sceneType,
                           newScene.ingestLocation,
                           newScene.tileFootprint) match {
        case (Some(SceneType.COG), Some(ingestLocation), None) => {
          logger.debug(s"Ingest location is: $ingestLocation")
          logger.info(s"Generating Footprint for Newly Added COG")
          CogUtils.getTiffExtent(ingestLocation)
        }
        case (_, _, tf @ Some(_)) => {
          logger.info("Not generating footprint, already exists")
          tf
        }
        case _ => None
      }

      val histogram: OptionT[Future, Array[Histogram[Double]]] =
        (newScene.sceneType, newScene.ingestLocation) match {
          case (Some(SceneType.COG), Some(ingestLocation)) =>
            CogUtils.fromUri(ingestLocation) map { _.tiff } map { geoTiff =>
              CogUtils.geoTiffDoubleHistogram(geoTiff)
            }
          case _ => OptionT.fromOption(None)
        }

      val dataFootprint = (tileFootprint, newScene.dataFootprint) match {
        case (Some(tf), None) => tileFootprint
        case _                => newScene.dataFootprint
      }

      val updatedScene = newScene.copy(dataFootprint = dataFootprint,
                                       tileFootprint = tileFootprint)

      val histogramAndInsertFut = for {
        insertedScene <- OptionT.liftF(
          SceneDao.insert(updatedScene, user).transact(xa).unsafeToFuture)
        _ <- if (!newScene.ingestLocation.isEmpty) {
          histogram flatMap { hist =>
            OptionT {
              parse(hist.toJson.toString) traverse { parsed =>
                LayerAttributeDao
                  .insertLayerAttribute(
                    LayerAttribute(insertedScene.id.toString,
                                   0,
                                   "histogram",
                                   parsed)
                  )
                  .transact(xa)
                  .unsafeToFuture
              } map { _.toOption }
            }
          }
        } else {
          // We have to return some kind of successful OptionT, so just provide
          // a small int value wrapped in a Some()
          // If we _don't_ do this, we can't post scenes without ingest locations,
          // which is bad.
          OptionT(Future.successful(Option(0)))
        }
      } yield insertedScene

      onSuccess(histogramAndInsertFut.value) {
        case Some(scene) =>
          if (scene.statusFields.ingestStatus == IngestStatus.ToBeIngested)
            kickoffSceneIngest(scene.id)
          complete((StatusCodes.Created, scene))
        case None =>
          complete(StatusCodes.BadRequest)
      }
    }
  }

  def getScene(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao
        .authQuery(user, ObjectType.Scene)
        .filter(sceneId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      rejectEmptyResponse {
        complete {
          SceneWithRelatedDao.getScene(sceneId).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def updateScene(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao
        .authorized(user, ObjectType.Scene, sceneId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[Scene]) { updatedScene =>
        onSuccess(
          SceneDao
            .update(updatedScene, sceneId, user)
            .transact(xa)
            .unsafeToFuture) {
          completeSingleOrNotFound(_)
        }
      }
    }
  }

  def deleteScene(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao
        .authorized(user, ObjectType.Scene, sceneId, ActionType.Delete)
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(
        SceneDao.query.filter(sceneId).delete.transact(xa).unsafeToFuture) {
        completeSingleOrNotFound
      }
    }
  }

  def getDownloadUrl(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneWithRelatedDao
        .authorized(user, ObjectType.Scene, sceneId, ActionType.Download)
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(
        SceneWithRelatedDao.getScene(sceneId).transact(xa).unsafeToFuture) {
        scene =>
          complete {
            val retrievedScene = scene.getOrElse {
              throw new Exception(
                "Scene does not exist or is not accessible by this user")
            }
            val s3Client = S3()
            val whitelist = List(s"s3://$dataBucket")
            (retrievedScene.sceneType, retrievedScene.ingestLocation) match {
              case (Some(SceneType.COG), Some(ingestLocation)) =>
                val signedUrl = s3Client.maybeSignUri(ingestLocation, whitelist)
                List(
                  Image.Downloadable(
                    s"${sceneId}_COG.tif",
                    s"$ingestLocation",
                    signedUrl
                  )
                )
              case _ =>
                retrievedScene.images map { image =>
                  val downloadUri: String =
                    s3Client.maybeSignUri(image.sourceUri, whitelist)
                  image.toDownloadable(downloadUri)
                }
            }
          }
      }
    }
  }

  def listScenePermissions(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao
        .authorized(user, ObjectType.Scene, sceneId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      complete {
        SceneDao
          .getPermissions(sceneId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def replaceScenePermissions(sceneId: UUID): Route = authenticate { user =>
    entity(as[List[ObjectAccessControlRule]]) { acrList =>
      authorizeAsync {
        (SceneDao.authorized(user, ObjectType.Scene, sceneId, ActionType.Edit),
         acrList traverse { acr =>
           SceneDao.isValidPermission(acr, user)
         } map { _.foldLeft(true)(_ && _) }).tupled
          .map({ authTup =>
            authTup._1 && authTup._2
          })
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          SceneDao
            .replacePermissions(sceneId, acrList)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def addScenePermission(sceneId: UUID): Route = authenticate { user =>
    entity(as[ObjectAccessControlRule]) { acr =>
      authorizeAsync {
        (SceneDao.authorized(user, ObjectType.Scene, sceneId, ActionType.Edit),
         SceneDao.isValidPermission(acr, user)).tupled
          .map(authTup => authTup._1 && authTup._2)
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          SceneDao
            .addPermission(sceneId, acr)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def listUserSceneActions(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao
        .authQuery(user, ObjectType.Scene)
        .filter(sceneId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(
        SceneWithRelatedDao
          .unsafeGetScene(sceneId)
          .transact(xa)
          .unsafeToFuture
      ) { scene =>
        scene.owner == user.id match {
          case true => complete(List("*"))
          case false =>
            complete {
              SceneDao
                .listUserActions(user, sceneId)
                .transact(xa)
                .unsafeToFuture
            }
        }
      }
    }
  }

  def deleteScenePermissions(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao
        .authorized(user, ObjectType.Scene, sceneId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      complete {
        SceneDao
          .deletePermissions(sceneId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def getSceneDatasource(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao
        .authQuery(user, ObjectType.Scene)
        .filter(sceneId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(
        SceneDao.getSceneDatasource(sceneId).transact(xa).unsafeToFuture) {
        datasourceO =>
          complete { datasourceO }
      }
    }
  }
}
