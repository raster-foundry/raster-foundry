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
import com.azavea.rf.database.tables.{Shapes, Users}
import com.azavea.rf.database.{ActionRunner, Database}
import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.GeoJsonCodec._
import geotrellis.shapefile.ShapeFileReader
import better.files.{File => ScalaFile, _}

import akka.http.scaladsl.server.directives.FileInfo
import geotrellis.proj4.{CRS, LatLng, WebMercator}
import geotrellis.slick.Projected
import geotrellis.vector.reproject.Reproject

@JsonCodec
case class ShapeFeatureCollectionCreate (
  features: Seq[Shape.GeoJSONFeatureCreate]
)


trait ShapeRoutes extends Authentication
  with QueryParametersCommon
  with PaginationDirectives
  with CommonHandlers
  with UserErrorHandler
  with LazyLogging
  with ActionRunner {

  implicit def database: Database

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
                complete(StatusCodes.Created, Shapes.insertShapes(Seq(shape), user))
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
        list[Shape](
          Shapes.listShapes(page.offset, page.limit, queryParams, user),
          page.offset, page.limit
        ) map { p => {
            fromPaginatedResponseToGeoJson[Shape, Shape.GeoJSON](p)
          }
        }
      }
    }
  }

  def getShape(shapeId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        readOne[Shape](Shapes.getShape(shapeId, user)) map { _ map { _.toGeoJSONFeature } }
      }
    }
  }

  def createShape: Route = authenticate { user =>
    entity(as[ShapeFeatureCollectionCreate]) { fc =>
      val shapesCreate = fc.features map { _.toShapeCreate }
      complete {
        Shapes.insertShapes(shapesCreate, user)
      }
    }
  }

  def updateShape(shapeId: UUID): Route = authenticate { user =>
    entity(as[Shape.GeoJSON]) { updatedShape: Shape.GeoJSON =>
      authorize(user.isInRootOrSameOrganizationAs(updatedShape.properties)) {
        onSuccess(update(Shapes.updateShape(updatedShape, shapeId, user))) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteShape(shapeId: UUID): Route = authenticate { user =>
    onSuccess(drop(Shapes.deleteShape(shapeId, user))) {
      completeSingleOrNotFound
    }
  }
}
