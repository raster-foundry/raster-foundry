package com.rasterfoundry.api.scene

import com.rasterfoundry.akkautil.PaginationDirectives
import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  UserErrorHandler
}
import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.common.LayerAttribute
import com.rasterfoundry.common.utils.CogUtils
import com.rasterfoundry.common.{AWSBatch, S3}
import com.rasterfoundry.database._
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.implicits._
import doobie.util.transactor.Transactor
import geotrellis.raster.io.json.HistogramJsonFormats
import io.circe.syntax._

import scala.concurrent.ExecutionContext

import java.util.UUID
import java.util.concurrent.Executors

trait SceneRoutes
    extends Authentication
    with Config
    with SceneQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with AWSBatch
    with UserErrorHandler
    with HistogramJsonFormats {

  implicit val ec: ExecutionContext

  implicit val contextShift: ContextShift[IO]

  val rasterIOContext = ExecutionContext.fromExecutor(
    Executors.newFixedThreadPool(
      4,
      new ThreadFactoryBuilder().setNameFormat("geotiffinfo-%d").build()
    )
  )

  val rasterIOContextShift = IO.contextShift(rasterIOContext)

  val cogUtils = new CogUtils[IO](rasterIOContextShift, rasterIOContext)

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
          } ~
          pathPrefix("sentinel-metadata") {
            pathPrefix(Segment) { metadataUrl =>
              pathEndOrSingleSlash {
                get { getSentinelMetadata(sceneId, metadataUrl) }
              }
            }
          }
      }
  }

  def listScenes: Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Scenes, Action.Read, None), user) {
        (withPagination & sceneQueryParameters) { (page, sceneParams) =>
          complete {
            SceneWithRelatedDao
              .listAuthorizedScenes(page, sceneParams, user)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }

  def createScene: Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Scenes, Action.Create, None), user) {
        entity(as[Scene.Create]) { newScene =>
          val metadataIO = (sceneId: UUID) => {
            newScene.ingestLocation traverse { location =>
              (
                cogUtils.getGeoTiffInfo(location),
                cogUtils.histogramFromUri(location),
                cogUtils.getTiffExtent(location)
              ).tupled flatMap {
                case (geotiffInfo, histogram, footprint) =>
                  ((LayerAttributeDao
                    .insertLayerAttribute(
                      LayerAttribute(
                        sceneId.toString,
                        0,
                        "histogram",
                        histogram.asJson
                      )
                    )
                    .attempt map {
                    _.toOption
                  }) *> SceneDao
                    .updateSceneGeoTiffInfo(
                      geotiffInfo,
                      sceneId
                    ) *> SceneDao.updateFootprints(
                    sceneId,
                    footprint,
                    footprint,
                    user
                  ) *> SceneDao.markIngested(sceneId)).transact(xa)
              }
            }
          }

          val uningestedScene = newScene
            .copy(
              statusFields = newScene.statusFields.copy(
                ingestStatus = IngestStatus.NotIngested
              )
            )
          val sceneInsert = (
            newScene.sceneType,
            !newScene.ingestLocation.isEmpty
          ) match {
            case (Some(SceneType.COG), true) =>
              for {
                insertedScene <- SceneDao
                  .insert(uningestedScene, user)
                  .transact(xa)
                _ <- metadataIO(insertedScene.id).start
              } yield insertedScene

            case (_, false) =>
              SceneDao.insert(uningestedScene, user).transact(xa)
            case _ =>
              throw new IllegalArgumentException(
                "Unable to generate histograms for scene. Please verify that appropriate " ++
                  "overviews exist. Histogram generation requires an overview smaller than 600x600."
              )
          }

          onSuccess(sceneInsert.unsafeToFuture) { scene =>
            if (scene.statusFields.ingestStatus == IngestStatus.ToBeIngested)
              kickoffSceneIngest(scene.id)
            complete((StatusCodes.Created, scene))
          }
        }
      }
    }

  def getScene(sceneId: UUID): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Scenes, Action.Read, None), user) {
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
    }

  def updateScene(sceneId: UUID): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Scenes, Action.Update, None), user) {
        authorizeAuthResultAsync {
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
                .unsafeToFuture
            ) {
              completeSingleOrNotFound(_)
            }
          }
        }
      }
    }

  def deleteScene(sceneId: UUID): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Scenes, Action.Delete, None), user) {
        authorizeAuthResultAsync {
          SceneDao
            .authorized(user, ObjectType.Scene, sceneId, ActionType.Delete)
            .transact(xa)
            .unsafeToFuture
        } {
          val response = for {
            result <- SceneDao.query.filter(sceneId).delete.transact(xa)
            _ <- SceneDao.deleteCache(sceneId).transact(xa)
          } yield result
          onSuccess(response.unsafeToFuture) {
            completeSingleOrNotFound
          }
        }
      }
    }

  def getDownloadUrl(sceneId: UUID): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Scenes, Action.Download, None), user) {
        authorizeAuthResultAsync {
          SceneWithRelatedDao
            .authorized(user, ObjectType.Scene, sceneId, ActionType.Download)
            .transact(xa)
            .unsafeToFuture
        } {
          onSuccess(
            SceneWithRelatedDao.getScene(sceneId).transact(xa).unsafeToFuture
          ) { scene =>
            complete {
              val retrievedScene = scene.getOrElse {
                throw new Exception(
                  "Scene does not exist or is not accessible by this user"
                )
              }
              val s3Client = S3()
              val whitelist = List(s"s3://$dataBucket")
              (retrievedScene.sceneType, retrievedScene.ingestLocation) match {
                case (Some(SceneType.COG), Some(ingestLocation)) =>
                  val signedUrl =
                    s3Client.maybeSignUri(ingestLocation, whitelist)
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
    }

  def listScenePermissions(sceneId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Scenes, Action.ReadPermissions, None),
        user
      ) {
        authorizeAuthResultAsync {
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
    }

  def replaceScenePermissions(sceneId: UUID): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Scenes, Action.Share, None), user) {
        entity(as[List[ObjectAccessControlRule]]) { acrList =>
          authorizeAsync {
            (
              SceneDao
                .authorized(
                  user,
                  ObjectType.Scene,
                  sceneId,
                  ActionType.Edit
                ) map {
                _.toBoolean
              },
              acrList traverse { acr =>
                SceneDao.isValidPermission(acr, user)
              } map {
                _.foldLeft(true)(_ && _)
              } map {
                case true =>
                  SceneDao.isReplaceWithinScopedLimit(
                    Domain.Scenes,
                    user,
                    acrList
                  )
                case _ => false
              }
            ).tupled
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
    }

  def addScenePermission(sceneId: UUID): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Scenes, Action.Share, None), user) {
        entity(as[ObjectAccessControlRule]) { acr =>
          authorizeAsync {
            (
              SceneDao
                .authorized(
                  user,
                  ObjectType.Scene,
                  sceneId,
                  ActionType.Edit
                ) map {
                _.toBoolean
              },
              SceneDao.isValidPermission(acr, user)
            ).tupled
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
    }

  def listUserSceneActions(sceneId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Scenes, Action.ReadPermissions, None),
        user
      ) {
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
    }

  def deleteScenePermissions(sceneId: UUID): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Scenes, Action.Share, None), user) {
        authorizeAuthResultAsync {
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
    }

  def getSceneDatasource(sceneId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Datasources, Action.Read, None),
        user
      ) {
        authorizeAsync {
          SceneDao
            .authQuery(user, ObjectType.Scene)
            .filter(sceneId)
            .exists
            .transact(xa)
            .unsafeToFuture
        } {
          onSuccess(
            DatasourceDao
              .getSceneDatasource(sceneId)
              .transact(xa)
              .unsafeToFuture
          ) { datasourceO =>
            complete {
              datasourceO
            }
          }
        }
      }
    }

  def getSentinelMetadata(sceneId: UUID, metadataUrl: String): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Scenes, Action.ReadSentinelMetadata, None),
        user
      ) {
        authorizeAsync {
          val authorizedIO = for {
            auth <- SceneDao.authorized(
              user,
              ObjectType.Scene,
              sceneId,
              ActionType.View
            ) map {
              _.toBoolean
            }
            datasource <- DatasourceDao.getSceneDatasource(sceneId)
          } yield {
            auth && (datasource map { (ds: Datasource) =>
              Some(ds.id) == Some(UUID.fromString(sentinel2DatasourceId))
            } getOrElse {
              false
            })
          }
          authorizedIO.transact(xa).unsafeToFuture
        } {
          onSuccess(
            SceneDao
              .getSentinelMetadata(metadataUrl)
              .transact(xa)
              .unsafeToFuture
          ) { (s3Object, metaData) =>
            metaData.getContentType() match {
              case "application/json" =>
                complete(
                  HttpResponse(
                    entity =
                      HttpEntity(ContentTypes.`application/json`, s3Object)
                  )
                )
              case "application/xml" =>
                complete(
                  HttpResponse(
                    entity =
                      HttpEntity(ContentTypes.`text/xml(UTF-8)`, s3Object)
                  )
                )
              case _ =>
                complete(StatusCodes.UnsupportedMediaType)
            }
          }
        }
      }
    }
}
