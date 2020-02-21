package com.rasterfoundry.api.shape

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.ShapeDao
import com.rasterfoundry.datamodel.GeoJsonCodec._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.circe.generic.JsonCodec

import scala.concurrent.ExecutionContext

import java.util.UUID

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
  implicit val ec: ExecutionContext

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

  def listShapes: Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Shapes, Action.Read, None), user) {
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
              .page(page)
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
  }

  def getShape(shapeId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Shapes, Action.Read, None), user) {
      authorizeAuthResultAsync {
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
                _ map {
                  _.toGeoJSONFeature
                }
              }
          }
        }
      }
    }
  }

  def createShape: Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Shapes, Action.Create, None), user) {
      entity(as[ShapeFeatureCollectionCreate]) { fc =>
        val shapesCreate = fc.features map {
          _.toShapeCreate
        }
        complete {
          ShapeDao
            .insertShapes(shapesCreate, user)
            .transact(xa)
            .unsafeToFuture()
        }
      }
    }
  }

  def updateShape(shapeId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Shapes, Action.Update, None), user) {
      authorizeAuthResultAsync {
        ShapeDao
          .authorized(user, ObjectType.Shape, shapeId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[Shape.GeoJSON]) { updatedShape: Shape.GeoJSON =>
          onSuccess(
            ShapeDao
              .updateShape(updatedShape, shapeId)
              .transact(xa)
              .unsafeToFuture()
          ) {
            completeSingleOrNotFound
          }
        }
      }
    }
  }

  def deleteShape(shapeId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Shapes, Action.Delete, None), user) {
      authorizeAuthResultAsync {
        ShapeDao
          .authorized(user, ObjectType.Shape, shapeId, ActionType.Delete)
          .transact(xa)
          .unsafeToFuture
      } {
        onSuccess(
          ShapeDao.query.filter(shapeId).delete.transact(xa).unsafeToFuture
        ) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def listShapePermissions(shapeId: UUID): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.Shapes, Action.ReadPermissions, None),
      user
    ) {
      authorizeAuthResultAsync {
        ShapeDao
          .authorized(user, ObjectType.Shape, shapeId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          ShapeDao
            .getPermissions(shapeId)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def replaceShapePermissions(shapeId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Shapes, Action.Share, None), user) {
      entity(as[List[ObjectAccessControlRule]]) { acrList =>
        authorizeAsync {
          (
            ShapeDao
              .authorized(user, ObjectType.Shape, shapeId, ActionType.Edit) map {
              _.toBoolean
            },
            acrList traverse { acr =>
              ShapeDao.isValidPermission(acr, user)
            } map {
              _.foldLeft(true)(_ && _)
            }
          ).tupled
            .map({ authTup =>
              authTup._1 && authTup._2
            })
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            ShapeDao
              .replacePermissions(shapeId, acrList)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
  }

  def addShapePermission(shapeId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Shapes, Action.Share, None), user) {
      entity(as[ObjectAccessControlRule]) { acr =>
        authorizeAsync {
          (
            ShapeDao
              .authorized(user, ObjectType.Shape, shapeId, ActionType.Edit) map {
              _.toBoolean
            },
            ShapeDao.isValidPermission(acr, user)
          ).tupled
            .map({ authTup =>
              authTup._1 && authTup._2
            })
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            ShapeDao
              .addPermission(shapeId, acr)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
  }

  def listUserShapeActions(shapeId: UUID): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.Shapes, Action.ReadPermissions, None),
      user
    ) {
      authorizeAuthResultAsync {
        ShapeDao
          .authorized(user, ObjectType.Shape, shapeId, ActionType.View)
          .transact(xa)
          .unsafeToFuture
      } {
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
    authorizeScope(ScopedAction(Domain.Shapes, Action.Share, None), user) {
      authorizeAuthResultAsync {
        ShapeDao
          .authorized(user, ObjectType.Shape, shapeId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
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
}
