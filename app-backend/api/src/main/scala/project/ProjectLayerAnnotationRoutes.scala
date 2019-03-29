package com.rasterfoundry.api.project

import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.common.datamodel.GeoJsonCodec._
import com.rasterfoundry.database._
import com.rasterfoundry.akkautil.{Authentication, CommonHandlers}

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import cats.effect._
import com.lonelyplanet.akka.http.extensions.{PageRequest, PaginationDirectives}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.Transactor
import doobie.implicits._

import scala.concurrent.ExecutionContext.Implicits.global

import java.util.UUID

trait ProjectLayerAnnotationRoutes
    extends Authentication
    with CommonHandlers
    with PaginationDirectives
    with ProjectAuthorizationDirectives
    with QueryParametersCommon {

  implicit val xa: Transactor[IO]

  def listLayerLabels(projectId: UUID, layerId: UUID): Route = authenticate {
    user =>
      authorizeAsync {
        ProjectDao
          .authProjectLayerExist(projectId, layerId, user, ActionType.View)
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          AnnotationDao
            .listProjectLabels(projectId, Some(layerId))
            .transact(xa)
            .unsafeToFuture
        }
      }
  }

  def listLayerAnnotations(projectId: UUID, layerId: UUID): Route =
    extractTokenHeader { tokenO =>
      extractMapTokenParam { mapTokenO =>
        (projectAuthFromMapTokenO(mapTokenO, projectId) |
          projectAuthFromTokenO(tokenO, projectId) | projectIsPublic(projectId)) {
          (withPagination & annotationQueryParams) {
            (page: PageRequest, queryParams: AnnotationQueryParameters) =>
              complete {
                AnnotationDao
                  .listByLayer(projectId, page, queryParams, Some(layerId))
                  .transact(xa)
                  .unsafeToFuture
                  .map { p =>
                    {
                      fromPaginatedResponseToGeoJson[
                        Annotation,
                        Annotation.GeoJSON
                      ](p)
                    }
                  }
              }
          }
        }
      }
    }

  def createLayerAnnotation(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      authorizeAsync {
        ProjectDao
          .authProjectLayerExist(projectId, layerId, user, ActionType.Annotate)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[AnnotationFeatureCollectionCreate]) { fc =>
          val annotationsCreate = fc.features map { _.toAnnotationCreate }
          onSuccess(
            AnnotationDao
              .insertAnnotations(annotationsCreate.toList,
                                 projectId,
                                 user,
                                 Some(layerId))
              .transact(xa)
              .unsafeToFuture
              .map { annotations: List[Annotation] =>
                fromSeqToFeatureCollection[Annotation, Annotation.GeoJSON](
                  annotations)
              }
          ) { createdAnnotation =>
            complete((StatusCodes.Created, createdAnnotation))
          }
        }
      }
    }

  def deleteLayerAnnotations(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      authorizeAsync {
        ProjectDao
          .authProjectLayerExist(projectId, layerId, user, ActionType.Annotate)
          .transact(xa)
          .unsafeToFuture
      } {
        onSuccess(
          AnnotationDao
            .deleteByProjectLayer(projectId, Some(layerId))
            .transact(xa)
            .unsafeToFuture) {
          completeSomeOrNotFound
        }
      }
    }

  def getLayerAnnotation(projectId: UUID,
                         annotationId: UUID,
                         layerId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authProjectLayerExist(projectId, layerId, user, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      rejectEmptyResponse {
        complete {
          AnnotationDao
            .getAnnotationById(projectId, annotationId)
            .transact(xa)
            .unsafeToFuture
            .map {
              _ map { _.toGeoJSONFeature }
            }
        }
      }
    }
  }
  def updateLayerAnnotation(projectId: UUID,
                            annotationId: UUID,
                            layerId: UUID): Route =
    authenticate { user =>
      authorizeAsync {
        ProjectDao
          .authProjectLayerExist(projectId, layerId, user, ActionType.Annotate)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[Annotation.GeoJSON]) {
          updatedAnnotation: Annotation.GeoJSON =>
            onSuccess(AnnotationDao
              .updateAnnotation(projectId, updatedAnnotation.toAnnotation, user)
              .transact(xa)
              .unsafeToFuture) { count =>
              completeSingleOrNotFound(count)
            }
        }
      }
    }

  def deleteLayerAnnotation(projectId: UUID,
                            annotationId: UUID,
                            layerId: UUID): Route =
    authenticate { user =>
      authorizeAsync {
        ProjectDao
          .authProjectLayerExist(projectId, layerId, user, ActionType.Annotate)
          .transact(xa)
          .unsafeToFuture
      } {
        onSuccess(
          AnnotationDao
            .deleteById(projectId, annotationId)
            .transact(xa)
            .unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }

  def exportLayerAnnotationShapefile(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      authorizeAsync {
        ProjectDao
          .authProjectLayerExist(projectId, layerId, user, ActionType.View)
          .transact(xa)
          .unsafeToFuture
      } {
        onSuccess(
          AnnotationDao
            .listForLayerExport(projectId, layerId)
            .transact(xa)
            .unsafeToFuture) {
          case annotations @ (annotation: List[Annotation]) => {
            complete(
              AnnotationShapefileService
                .getAnnotationShapefileDownloadUrl(annotations, user)
            )
          }
          case _ =>
            complete(
              throw new Exception(
                "Annotations do not exist or are not accessible by this user"))
        }
      }
    }

  def listLayerAnnotationGroups(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      authorizeAsync {
        ProjectDao
          .authProjectLayerExist(projectId, layerId, user, ActionType.View)
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          AnnotationGroupDao
            .listForProject(projectId, Some(layerId))
            .transact(xa)
            .unsafeToFuture
        }
      }
    }

  def createLayerAnnotationGroup(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      authorizeAsync {
        ProjectDao
          .authProjectLayerExist(projectId, layerId, user, ActionType.Annotate)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[AnnotationGroup.Create]) { agCreate =>
          complete {
            AnnotationGroupDao
              .createAnnotationGroup(projectId, agCreate, user, Some(layerId))
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }

  def getLayerAnnotationGroup(projectId: UUID,
                              layerId: UUID,
                              agId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authProjectLayerExist(projectId, layerId, user, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      complete {
        AnnotationGroupDao
          .getAnnotationGroup(projectId, agId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def updateLayerAnnotationGroup(projectId: UUID,
                                 layerId: UUID,
                                 agId: UUID): Route =
    authenticate { user =>
      authorizeAsync {
        ProjectDao
          .authProjectLayerExist(projectId, layerId, user, ActionType.Annotate)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[AnnotationGroup]) { annotationGroup =>
          complete {
            AnnotationGroupDao
              .updateAnnotationGroup(projectId, annotationGroup, agId, user)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }

  def deleteLayerAnnotationGroup(projectId: UUID,
                                 layerId: UUID,
                                 agId: UUID): Route =
    authenticate { user =>
      authorizeAsync {
        ProjectDao
          .authProjectLayerExist(projectId, layerId, user, ActionType.Annotate)
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          AnnotationGroupDao
            .deleteAnnotationGroup(projectId, agId)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }

  def getLayerAnnotationGroupSummary(projectId: UUID,
                                     layerId: UUID,
                                     annotationGroupId: UUID): Route =
    authenticate { user =>
      authorizeAsync {
        ProjectDao
          .authProjectLayerExist(projectId, layerId, user, ActionType.View)
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          AnnotationGroupDao
            .getAnnotationGroupSummary(projectId,
                                       annotationGroupId,
                                       Some(layerId))
            .transact(xa)
            .unsafeToFuture
        }
      }
    }

}
