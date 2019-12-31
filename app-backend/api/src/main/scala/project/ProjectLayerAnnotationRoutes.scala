package com.rasterfoundry.api.project

import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.GeoJsonCodec._
import com.rasterfoundry.database._
import com.rasterfoundry.akkautil._
import com.rasterfoundry.common.AWSBatch

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import cats.effect._
import cats.implicits._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.Transactor
import doobie.implicits._

import scala.concurrent.ExecutionContext

import java.util.UUID

trait ProjectLayerAnnotationRoutes
    extends Authentication
    with CommonHandlers
    with PaginationDirectives
    with ProjectAuthorizationDirectives
    with QueryParametersCommon
    with AWSBatch {

  implicit val xa: Transactor[IO]

  implicit val ec: ExecutionContext

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
          projectAuthFromTokenO(
            tokenO,
            projectId,
            None,
            ScopedAction(Domain.Projects, Action.Read, None)
          ) | projectIsPublic(projectId)) {
          (withPagination & annotationQueryParams) {
            (page: PageRequest, queryParams: AnnotationQueryParameters) =>
              complete {
                (queryParams.withOwnerInfo match {
                  case Some(true) =>
                    AnnotationDao
                      .listByLayerWithOwnerInfo(
                        projectId,
                        page,
                        queryParams,
                        Some(layerId)
                      )
                      .transact(xa)
                      .unsafeToFuture
                      .map { p =>
                        {
                          fromPaginatedResponseToGeoJson[
                            AnnotationWithOwnerInfo,
                            AnnotationWithOwnerInfo.GeoJSON
                          ](p)
                        }
                      }
                  case _ =>
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
                })
              }
          }
        }
      }
    }

  def createLayerAnnotation(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.CreateAnnotation, None),
        user
      ) {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(
              projectId,
              layerId,
              user,
              ActionType.Annotate
            )
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[AnnotationFeatureCollectionCreate]) { fc =>
            val annotationsCreate = fc.features map {
              _.toAnnotationCreate
            }
            onSuccess(
              AnnotationDao
                .insertAnnotations(
                  annotationsCreate.toList,
                  projectId,
                  user,
                  Some(layerId)
                )
                .transact(xa)
                .unsafeToFuture
                .map { annotations: List[Annotation] =>
                  fromSeqToFeatureCollection[Annotation, Annotation.GeoJSON](
                    annotations
                  )
                }
            ) { createdAnnotation =>
              complete((StatusCodes.Created, createdAnnotation))
            }
          }
        }
      }
    }

  def deleteLayerAnnotations(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.DeleteAnnotation, None),
        user
      ) {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(
              projectId,
              layerId,
              user,
              ActionType.Annotate
            )
            .transact(xa)
            .unsafeToFuture
        } {
          onSuccess(
            AnnotationDao
              .deleteByProjectLayer(projectId, Some(layerId))
              .transact(xa)
              .unsafeToFuture
          ) {
            completeSomeOrNotFound
          }
        }
      }
    }

  def getLayerAnnotation(
      projectId: UUID,
      annotationId: UUID,
      layerId: UUID
  ): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Projects, Action.Read, None), user) {
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
                _ map {
                  _.toGeoJSONFeature
                }
              }
          }
        }
      }
    }
  }
  def updateLayerAnnotation(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.UpdateAnnotation, None),
        user
      ) {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(
              projectId,
              layerId,
              user,
              ActionType.Annotate
            )
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[Annotation.GeoJSON]) {
            updatedAnnotation: Annotation.GeoJSON =>
              onSuccess(
                AnnotationDao
                  .updateAnnotation(projectId, updatedAnnotation.toAnnotation)
                  .transact(xa)
                  .unsafeToFuture
              ) { count =>
                completeSingleOrNotFound(count)
              }
          }
        }
      }
    }

  def deleteLayerAnnotation(
      projectId: UUID,
      annotationId: UUID,
      layerId: UUID
  ): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.DeleteAnnotation, None),
        user
      ) {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(
              projectId,
              layerId,
              user,
              ActionType.Annotate
            )
            .transact(xa)
            .unsafeToFuture
        } {
          onSuccess(
            AnnotationDao
              .deleteById(projectId, annotationId)
              .transact(xa)
              .unsafeToFuture
          ) {
            completeSingleOrNotFound
          }
        }
      }
    }

  def listLayerAnnotationGroups(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationGroups, Action.Read, None),
        user
      ) {
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
    }

  def createLayerAnnotationGroup(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationGroups, Action.Create, None),
        user
      ) {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(
              projectId,
              layerId,
              user,
              ActionType.Annotate
            )
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
    }

  def getLayerAnnotationGroup(
      projectId: UUID,
      layerId: UUID,
      agId: UUID
  ): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.AnnotationGroups, Action.Read, None),
      user
    ) {
      authorizeAsync {
        AnnotationGroupDao
          .authAnnotationGroupExists(
            projectId,
            layerId,
            agId,
            user,
            ActionType.View
          )
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
  }

  def updateLayerAnnotationGroup(
      projectId: UUID,
      layerId: UUID,
      agId: UUID
  ): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationGroups, Action.Update, None),
        user
      ) {
        authorizeAsync {
          AnnotationGroupDao
            .authAnnotationGroupExists(
              projectId,
              layerId,
              agId,
              user,
              ActionType.Annotate
            )
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[AnnotationGroup]) { annotationGroup =>
            complete {
              AnnotationGroupDao
                .updateAnnotationGroup(projectId, annotationGroup, agId)
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
    }

  def deleteLayerAnnotationGroup(
      projectId: UUID,
      layerId: UUID,
      agId: UUID
  ): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationGroups, Action.Delete, None),
        user
      ) {
        authorizeAsync {
          AnnotationGroupDao
            .authAnnotationGroupExists(
              projectId,
              layerId,
              agId,
              user,
              ActionType.Annotate
            )
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
    }

  def getLayerAnnotationGroupSummary(
      projectId: UUID,
      layerId: UUID,
      annotationGroupId: UUID
  ): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationGroups, Action.Read, None),
        user
      ) {
        authorizeAsync {
          AnnotationGroupDao
            .authAnnotationGroupExists(
              projectId,
              layerId,
              annotationGroupId,
              user,
              ActionType.View
            )
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            AnnotationGroupDao
              .getAnnotationGroupSummary(
                annotationGroupId
              )
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }

  def listGeojsonUploads(
      projectId: UUID,
      layerId: UUID,
      annotationGroupId: UUID
  ): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationUploads, Action.Read, None),
        user
      ) {
        withPagination { page =>
          authorizeAuthResultAsync {
            ProjectDao
              .authorized(
                user,
                ObjectType.Project,
                projectId,
                ActionType.Annotate
              )
              .transact(xa)
              .unsafeToFuture
          } {
            complete {
              GeojsonUploadDao
                .listUploadsForAnnotationGroup(
                  projectId,
                  layerId,
                  annotationGroupId,
                  page
                )
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
    }

  def createGeojsonUpload(
      projectId: UUID,
      layerId: UUID,
      annotationGroupId: UUID
  ): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationUploads, Action.Create, None),
        user
      ) {
        authorizeAuthResultAsync {
          ProjectDao
            .authorized(
              user,
              ObjectType.Project,
              projectId,
              ActionType.Annotate
            )
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[GeojsonUpload.Create]) { newUpload =>
            logger.debug(
              s"newUpload: ${newUpload.uploadType}, ${newUpload.files}, ${newUpload.fileType}"
            )
            val uploadToInsert =
              (
                newUpload.uploadType,
                newUpload.files.nonEmpty,
                newUpload.fileType
              ) match {
                case (UploadType.S3, true, FileType.GeoJson) => {
                  newUpload
                }
                case (_, false, _) => {
                  throw new IllegalStateException(
                    "Uploads must specify file URIs"
                  )
                }
                case (_, _, fileType) if fileType.equals(FileType.GeoJson) => {
                  throw new IllegalStateException(
                    "Unsupported file type. Please use GeoJson"
                  )
                }
                case _ => {
                  throw new IllegalStateException(
                    "Non-S3 uploads currently unsupported"
                  )
                }
              }

            onSuccess({
              val uploadIO = AnnotationGroupDao
                .getAnnotationGroup(projectId, annotationGroupId)
                .flatMap {
                  _ traverse { _ =>
                    GeojsonUploadDao
                      .insert(
                        uploadToInsert,
                        projectId,
                        layerId,
                        annotationGroupId,
                        user
                      )
                  }
                }
              uploadIO.transact(xa).unsafeToFuture
            }) { uploadO =>
              uploadO match {
                case Some(upload) =>
                  if (upload.uploadStatus.equals(UploadStatus.Uploaded)) {
                    kickoffGeojsonImport(upload.id)
                  }
                  complete((StatusCodes.Created, upload))
                case _ =>
                  complete(StatusCodes.NotFound)
              }
            }
          }
        }
      }
    }

  def getGeojsonUpload(
      projectId: UUID,
      layerId: UUID,
      annotationGroupId: UUID,
      uploadId: UUID
  ): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.AnnotationUploads, Action.Read, None),
      user
    ) {
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Annotate)
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          GeojsonUploadDao
            .getLayerUpload(projectId, layerId, annotationGroupId, uploadId)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def updateGeojsonUpload(
      projectId: UUID,
      projectLayerId: UUID,
      annotationGroupId: UUID,
      uploadId: UUID
  ): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.AnnotationUploads, Action.Update, None),
      user
    ) {
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Annotate)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[GeojsonUpload]) { upload =>
          complete {
            GeojsonUploadDao
              .update(
                upload,
                projectId,
                projectLayerId,
                annotationGroupId,
                uploadId,
                user
              )
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
  }

  def deleteGeojsonUpload(
      projectId: UUID,
      layerId: UUID,
      annotationGroupId: UUID,
      uploadId: UUID
  ): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.AnnotationUploads, Action.Delete, None),
      user
    ) {
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Annotate)
          .transact(xa)
          .unsafeToFuture
      } {
        onSuccess(
          GeojsonUploadDao
            .deleteProjectLayerUpload(
              projectId,
              layerId,
              annotationGroupId,
              uploadId
            )
            .transact(xa)
            .unsafeToFuture
        ) {
          completeSingleOrNotFound
        }
      }
    }
  }
}
