package com.azavea.rf.api.shape

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import better.files.{File => ScalaFile}
import cats.effect.IO
import com.azavea.rf.api.utils.queryparams.QueryParametersCommon
import com.azavea.rf.authentication.Authentication
import com.azavea.rf.common._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.ShapeDao
import com.azavea.rf.datamodel.GeoJsonCodec._
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.{PageRequest, PaginationDirectives}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.shapefile.ShapeFileReader
import geotrellis.vector.Projected
import geotrellis.vector.reproject.Reproject
import io.circe.generic.JsonCodec

import scala.concurrent.ExecutionContext.Implicits.global

@JsonCodec
final case class ShapeFeatureCollectionCreate(
    features: Seq[Shape.GeoJSONFeatureCreate]
)

trait ShapeRoutes
    extends Authentication
    with QueryParametersCommon
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with LazyLogging {

  val xa: Transactor[IO]

  val shapeRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listShapes } ~
        post { createShape }
    } ~
      post {
        pathPrefix("upload") {
          pathEndOrSingleSlash {
            authenticate { user =>
              val tempFile = ScalaFile.newTemporaryFile()
              tempFile.deleteOnExit()
              val response = storeUploadedFile("name", (_) => tempFile.toJava) {
                (m, _) =>
                  processShapefile(user, tempFile, m)
              }
              tempFile.delete()
              response
            }
          }
        }
      } ~
      pathPrefix(JavaUUID) { shapeId =>
        pathEndOrSingleSlash {
          get { getShape(shapeId) } ~
            put { updateShape(shapeId) } ~
            delete { deleteShape(shapeId) }
        } ~
          pathPrefix("permissions") {
            pathEndOrSingleSlash {
              put {
                replaceShapePermissions(shapeId)
              }
            } ~
              post {
                addShapePermission(shapeId)
              } ~
              get {
                listShapePermissions(shapeId)
              } ~
              delete {
                deleteShapePermissions(shapeId)
              }
          } ~
          pathPrefix("actions") {
            pathEndOrSingleSlash {
              get {
                listUserShapeActions(shapeId)
              }
            }
          }
      }
  }

  def processShapefile(user: User,
                       tempFile: ScalaFile,
                       fileMetadata: FileInfo) = {
    val unzipped = tempFile.unzip()
    val matches = unzipped.glob("*.shp")
    matches.hasNext match {
      case true => {
        val shapeFile = matches.next()
        val features =
          ShapeFileReader.readMultiPolygonFeatures(shapeFile.toString)

        // Only reads first feature
        features match {
          case Nil =>
            complete(
              StatusCodes.ClientError(400)(
                "Bad Request",
                "No MultiPolygons detected in Shapefile"))
          case feature +: _ => {
            val geometry = feature.geom
            val reprojectedGeometry =
              Projected(Reproject(geometry, LatLng, WebMercator), 3857)
            reprojectedGeometry.isValid match {
              case true => {
                val shape = Shape.Create(
                  Some(user.id),
                  fileMetadata.fileName,
                  None,
                  reprojectedGeometry
                )
                complete(StatusCodes.Created,
                         ShapeDao
                           .insertShapes(Seq(shape), user)
                           .transact(xa)
                           .unsafeToFuture)
              }
              case _ => {
                val reason =
                  "No valid MultiPolygons found, please ensure coordinates are in EPSG:4326 before uploading."
                complete(StatusCodes.ClientError(400)("Bad Request", reason))
              }
            }
          }
        }
      }
      case _ =>
        complete(
          StatusCodes.ClientError(400)("Bad Request",
                                       "No Shapefile Found in Archive"))
    }
  }

  def listShapes: Route = authenticate { user =>
    (withPagination & shapeQueryParams) {
      (page: PageRequest, queryParams: ShapeQueryParameters) =>
        complete {
          ShapeDao
            .authQuery(
              user,
              ObjectType.Shape,
              queryParams.ownershipTypeParams.ownershipType,
              queryParams.groupQueryParameters.groupType,
              queryParams.groupQueryParameters.groupId
            )
            .filter(queryParams)
            .page(page, fr"")
            .transact(xa)
            .unsafeToFuture()
            .map { p =>
              {
                fromPaginatedResponseToGeoJson[Shape, Shape.GeoJSON](p)
              }
            }
        }
    }
  }

  def getShape(shapeId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ShapeDao
        .authorized(user, ObjectType.Shape, shapeId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      rejectEmptyResponse {
        complete {
          ShapeDao.query
            .filter(shapeId)
            .selectOption
            .transact(xa)
            .unsafeToFuture()
            .map {
              _ map { _.toGeoJSONFeature }
            }
        }
      }
    }
  }

  def createShape: Route = authenticate { user =>
    entity(as[ShapeFeatureCollectionCreate]) { fc =>
      val shapesCreate = fc.features map { _.toShapeCreate }
      complete {
        ShapeDao.insertShapes(shapesCreate, user).transact(xa).unsafeToFuture()
      }
    }
  }

  def updateShape(shapeId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ShapeDao
        .authorized(user, ObjectType.Shape, shapeId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[Shape.GeoJSON]) { updatedShape: Shape.GeoJSON =>
        onSuccess(
          ShapeDao
            .updateShape(updatedShape, shapeId, user)
            .transact(xa)
            .unsafeToFuture()) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteShape(shapeId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ShapeDao
        .authorized(user, ObjectType.Shape, shapeId, ActionType.Delete)
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(
        ShapeDao.query.filter(shapeId).delete.transact(xa).unsafeToFuture) {
        completeSingleOrNotFound
      }
    }
  }

  def listShapePermissions(shapeId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ShapeDao.query.ownedBy(user, shapeId).exists.transact(xa).unsafeToFuture
    } {
      complete {
        ShapeDao
          .getPermissions(shapeId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def replaceShapePermissions(shapeId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ShapeDao.query.ownedBy(user, shapeId).exists.transact(xa).unsafeToFuture
    } {
      entity(as[List[ObjectAccessControlRule]]) { acrList =>
        complete {
          ShapeDao
            .replacePermissions(shapeId, acrList)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def addShapePermission(shapeId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ShapeDao.query.ownedBy(user, shapeId).exists.transact(xa).unsafeToFuture
    } {
      entity(as[ObjectAccessControlRule]) { acr =>
        complete {
          ShapeDao
            .addPermission(shapeId, acr)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def listUserShapeActions(shapeId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ShapeDao
        .authorized(user, ObjectType.Shape, shapeId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      user.isSuperuser match {
        case true => complete(List("*"))
        case false =>
          onSuccess(
            ShapeDao.unsafeGetShapeById(shapeId).transact(xa).unsafeToFuture
          ) { shape =>
            shape.owner == user.id match {
              case true => complete(List("*"))
              case false =>
                complete {
                  ShapeDao
                    .listUserActions(user, shapeId)
                    .transact(xa)
                    .unsafeToFuture
                }
            }
          }
      }
    }
  }

  def deleteShapePermissions(shapeId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ShapeDao.query.ownedBy(user, shapeId).exists.transact(xa).unsafeToFuture
    } {
      complete {
        ShapeDao
          .deletePermissions(shapeId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }
}
