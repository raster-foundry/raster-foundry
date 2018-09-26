package com.azavea.rf.api.scene

import java.util.UUID

import akka.http.scaladsl.model.{HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import cats.data._
import cats.effect.IO
import cats.implicits._
import com.amazonaws.services.s3.AmazonS3URI
import java.net.URLDecoder
import com.azavea.rf.api.utils.Config
import com.azavea.rf.authentication.Authentication
import com.azavea.rf.common.utils.CogUtils
import com.azavea.rf.common.{AWSBatch, CommonHandlers, S3, UserErrorHandler}
import com.azavea.rf.database._
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import geotrellis.raster.{IntArrayTile, MultibandTile}
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.io.json.HistogramJsonFormats
import io.circe.parser._
import kamon.akka.http.KamonTraceDirectives

import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait SceneRoutes
    extends Authentication
    with Config
    with SceneQueryParameterDirective
    with ThumbnailQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with AWSBatch
    with UserErrorHandler
    with HistogramJsonFormats
    with KamonTraceDirectives {

  val xa: Transactor[IO]

  val sceneRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get {
        traceName("scenes-list") {
          listScenes
        }
      } ~
        post {
          traceName("scenes-create") {
            createScene
          }
        }
    } ~
      pathPrefix(JavaUUID) { sceneId =>
        pathEndOrSingleSlash {
          get {
            traceName("scenes-detail") {
              getScene(sceneId)
            }
          } ~
            put {
              traceName("scenes-update") {
                updateScene(sceneId)
              }
            } ~
            delete {
              traceName("scenes-delete") {
                deleteScene(sceneId)
              }
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
            CogUtils.fromUri(ingestLocation) map { geoTiff =>
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
        hist <- histogram
        insertedScene <- OptionT.liftF(
          SceneDao.insert(updatedScene, user).transact(xa).unsafeToFuture)
        _ <- OptionT.liftF(
          // We consider the geotrellis upstring json format to be infallible, and "guarantee" presence
          // of the right projection
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
        )
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
            val whitelist = List(s"s3://$dataBucket")
            (retrievedScene.sceneType, retrievedScene.ingestLocation) match {
              case (Some(SceneType.COG), Some(ingestLocation)) =>
                val signedUrl = S3.maybeSignUri(ingestLocation, whitelist)
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
                    S3.maybeSignUri(image.sourceUri, whitelist)
                  image.toDownloadable(downloadUri)
                }
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
        SceneDao
          .getPermissions(sceneId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def replaceScenePermissions(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao.query.ownedBy(user, sceneId).exists.transact(xa).unsafeToFuture
    } {
      entity(as[List[ObjectAccessControlRule]]) { acrList =>
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
    authorizeAsync {
      SceneDao.query.ownedBy(user, sceneId).exists.transact(xa).unsafeToFuture
    } {
      entity(as[ObjectAccessControlRule]) { acr =>
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
      user.isSuperuser match {
        case true => complete(List("*"))
        case false =>
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
  }

  def deleteScenePermissions(sceneId: UUID): Route = authenticate { user =>
    authorizeAsync {
      SceneDao.query.ownedBy(user, sceneId).exists.transact(xa).unsafeToFuture
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

  // Safe to `bands.head`, since construction of a MultibandTile requires at least one band, so it's
  // impossible to have an empty one
  @SuppressWarnings(Array("TraversableHead"))
  def getSceneThumbnail(sceneId: UUID): Route = authenticateWithParameter {
    user =>
      {
        thumbnailQueryParameters { thumbnailParams =>
          {
            authorizeAsync {
              SceneDao
                .authQuery(user, ObjectType.Scene)
                .filter(sceneId)
                .exists
                .transact(xa)
                .unsafeToFuture
            } {
              complete {
                SceneDao
                  .unsafeGetSceneById(sceneId)
                  .transact(xa)
                  .unsafeToFuture >>= {
                  case Scene(_,
                             _,
                             _,
                             _,
                             _,
                             _,
                             _,
                             _,
                             _,
                             _,
                             _,
                             _,
                             _,
                             _,
                             Some(ingestLocation),
                             _,
                             _,
                             Some(SceneType.COG)) =>
                    CogUtils
                      .thumbnail(
                        ingestLocation,
                        thumbnailParams.width,
                        thumbnailParams.height,
                        thumbnailParams.red,
                        thumbnailParams.green,
                        thumbnailParams.blue,
                        thumbnailParams.floor
                      )
                      .map(
                        (tile: MultibandTile) => {
                          tile match {
                            case t if t.bands.length >= 3 =>
                              HttpEntity(MediaTypes.`image/png`,
                                         tile.renderPng.bytes)
                            case t if t.bands.nonEmpty =>
                              HttpEntity(MediaTypes.`image/png`,
                                         tile.bands.head.renderPng.bytes)
                            case t =>
                              HttpEntity(
                                MediaTypes.`image/png`,
                                IntArrayTile.fill(0, 256, 256).renderPng.bytes)
                          }
                        }
                      )
                      .value
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
