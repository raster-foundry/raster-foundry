package com.azavea.rf.api.shape

import java.util.UUID

import scala.concurrent.ExecutionContext.Implicits.global
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import com.typesafe.scalalogging.LazyLogging
import com.lonelyplanet.akka.http.extensions.{PageRequest, PaginationDirectives}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._
import com.azavea.rf.api.utils.queryparams.QueryParametersCommon
import com.azavea.rf.common._
import com.azavea.rf.datamodel._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel.GeoJsonCodec._
import geotrellis.shapefile.ShapeFileReader
import better.files.{File => ScalaFile, _}
import akka.http.scaladsl.server.directives.FileInfo
import cats.effect.IO
import com.azavea.rf.database.ShapeDao
import geotrellis.proj4.{CRS, LatLng, WebMercator}
import geotrellis.slick.Projected
import geotrellis.vector.reproject.Reproject

import doobie.util.transactor.Transactor
import com.azavea.rf.datamodel._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._

@JsonCodec
case class ShapeFeatureCollectionCreate (
  features: Seq[Shape.GeoJSONFeatureCreate]
)


trait ShapeRoutes extends Authentication
  with QueryParametersCommon
  with PaginationDirectives
  with CommonHandlers
  with UserErrorHandler
  with LazyLogging {

  implicit def xa: Transactor[IO]

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
              val response = storeUploadedFile("name", (_) => tempFile.toJava) { (m, _) =>
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
        }
      }
  }

  def processShapefile(user: User, tempFile: ScalaFile, fileMetadata: FileInfo) = {
    val unzipped = tempFile.unzip()
    val matches = unzipped.glob("*.shp")
    matches.hasNext match {
      case true => {
        val shapeFile = matches.next()
        val features = ShapeFileReader.readMultiPolygonFeatures(shapeFile.toString)

        // Only reads first feature
        features match {
          case Nil => complete(StatusCodes.ClientError(400)("Bad Request", "No MultiPolygons detected in Shapefile"))
          case feature +: _ => {
            val geometry = feature.geom
            val reprojectedGeometry = Projected(Reproject(geometry, LatLng, WebMercator), 3857)
            reprojectedGeometry.isValid match {
              case true => {
                val shape = Shape.create(
                  Some(user.id),
                  user.organizationId,
                  fileMetadata.fileName,
                  None,
                  Some(reprojectedGeometry)
                )
                complete(StatusCodes.Created, ShapeDao.insertShapes(Seq(shape), user).transact(xa).unsafeToFuture)
              }
              case _ => {
                val reason = "No valid MultiPolygons found, please ensure coordinates are in EPSG:4326 before uploading"
                complete(StatusCodes.ClientError(400)("Bad Request", reason))
              }
            }
          }
        }
      }
      case _ => complete(StatusCodes.ClientError(400)("Bad Request", "No Shapefile Found in Archive"))
    }
  }

  def listShapes: Route = authenticate { user =>
    (withPagination & shapeQueryParams) { (page: PageRequest, queryParams: ShapeQueryParameters) =>
      complete {
        ShapeDao.query.filter(queryParams).filter(user).page(page).transact(xa).unsafeToFuture().map { p => {
            fromPaginatedResponseToGeoJson[Shape, Shape.GeoJSON](p)
          }
        }
      }
    }
  }

  def getShape(shapeId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        ShapeDao.query.filter(fr"id = ${shapeId}").ownerFilter(user).selectOption.transact(xa).unsafeToFuture().map {
          _ map { _.toGeoJSONFeature }
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
    entity(as[Shape.GeoJSON]) { updatedShape: Shape.GeoJSON =>
      authorize(user.isInRootOrSameOrganizationAs(updatedShape.properties)) {
        onSuccess(ShapeDao.updateShape(updatedShape, shapeId, user).transact(xa).unsafeToFuture()) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteShape(shapeId: UUID): Route = authenticate { user =>
    onSuccess(ShapeDao.query.filter(fr"id = ${shapeId}").ownerFilter(user).delete.transact(xa).unsafeToFuture) {
      completeSingleOrNotFound
    }
  }
}
