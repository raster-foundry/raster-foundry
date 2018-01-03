package com.azavea.rf.api.shape

import java.net.URL
import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

import akka.http.scaladsl.server.{Route, PathMatcher}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import com.typesafe.scalalogging.LazyLogging
import com.lonelyplanet.akka.http.extensions.{PageRequest, PaginationDirectives}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._

import com.azavea.rf.api.utils.queryparams.QueryParametersCommon
import com.azavea.rf.common._
import com.azavea.rf.database.tables.{Shapes, Users}
import com.azavea.rf.database.query._
import com.azavea.rf.database.{ActionRunner, Database}
import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.GeoJsonCodec._

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
    pathPrefix(JavaUUID) { shapeId =>
      pathEndOrSingleSlash {
        get { getShape(shapeId) } ~
        put { updateShape(shapeId) } ~
        delete { deleteShape(shapeId) }
      }
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
