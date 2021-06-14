package com.rasterfoundry.api.tasks

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel.GeoJsonCodec._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import cats.implicits._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.implicits._
import doobie.postgres.implicits._

import scala.util.{Failure, Success}

import java.util.UUID

trait TaskRoutes
    extends Authentication
    with CommonHandlers
    with PaginationDirectives
    with QueryParametersCommon
    with UserErrorHandler {

  val taskRoutes = handleExceptions(userExceptionHandler) {
    pathPrefix("random") {
      pathEndOrSingleSlash {
        get { listTasks }
      }
    } ~ pathPrefix(JavaUUID) { taskId =>
      {
        pathPrefix("sessions") {
          pathEndOrSingleSlash {
            post {
              createTaskSession(taskId)
            } ~ get {
              listTaskSessions(taskId)
            }
          } ~ pathPrefix(JavaUUID) { sessionId =>
            {
              pathEndOrSingleSlash {
                get {
                  getTaskSession(taskId, sessionId)
                }
              } ~ pathPrefix("keep-alive") {
                put {
                  keepSessionAlive(taskId, sessionId)
                }
              } ~ pathPrefix("complete") {
                put {
                  completeSession(taskId, sessionId)
                }
              } ~ pathPrefix("labels") {
                pathEndOrSingleSlash {
                  get {
                    listSessionLabels(taskId, sessionId)
                  } ~ post {
                    createLabel(taskId, sessionId)
                  }
                } ~ pathPrefix(JavaUUID) { labelId =>
                  {
                    pathEndOrSingleSlash {
                      get {
                        getLabel(taskId, sessionId, labelId)
                      } ~ put {
                        updateLabel(taskId, sessionId, labelId)
                      } ~ delete {
                        deleteLabel(taskId, sessionId, labelId)
                      }
                    }
                  }
                }
              } ~ pathPrefix("bulk") {
                post {
                  bulkCreateSessionLabels(taskId, sessionId)
                } ~ put {
                  bulkUpdateSessionLabels(taskId, sessionId)
                }
              }
            }
          }
        }
      }
    } ~ pathPrefix("session") {
      pathEndOrSingleSlash {
        post { randomTaskSession }
      }
    }
  }

  def listTasks: Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.ReadTasks, None),
        user
      ) {
        (withPagination & annotationProjectQueryParameters & taskQueryParameters & parameters(
          'annotationProjectId.as[UUID].?
        )) {
          (page, annotationProjectParams, taskParams, annotationProjectIdOpt) =>
            onComplete {
              TaskDao
                .getRandomTaskFromProjects(
                  user,
                  annotationProjectParams,
                  annotationProjectIdOpt,
                  page.limit,
                  taskParams
                )
                .transact(xa)
                .unsafeToFuture
            } {
              case Success(Some(task)) =>
                complete { task }
              case Success(None) =>
                complete { HttpResponse(StatusCodes.OK) }
              case Failure(e) =>
                logger.error(e.getMessage)
                complete { HttpResponse(StatusCodes.BadRequest) }
            }
        }
      }
    }

  // To create a task session:
  // - the user needs to have access to the annotation project or campaign
  // - there should be no active sessions on the task
  // - the session type should match the current task status
  def createTaskSession(taskId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.CreateAnnotation, None),
        user
      ) {
        authorizeAsync {
          TaskSessionDao
            .authorized(
              taskId,
              user,
              ActionType.Annotate
            )
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[TaskSession.Create]) { taskSessionCreate =>
            onComplete {
              (for {
                hasActiveSession <- TaskSessionDao.hasActiveSessionByTaskId(
                  taskId)
                hasValidStatus <- TaskSessionDao.isSessionTypeMatchTaskStatus(
                  taskId,
                  taskSessionCreate.sessionType
                )
              } yield (!hasActiveSession, hasValidStatus))
                .transact(xa)
                .unsafeToFuture

            } {
              case Success((true, true)) =>
                complete {
                  TaskSessionDao
                    .insert(taskSessionCreate, user, taskId)
                    .transact(xa)
                    .unsafeToFuture
                }
              case Success((false, _)) =>
                complete { StatusCodes.Conflict -> "ACTIVE_SESSION_EXISTS" }
              case Success((_, false)) =>
                complete { StatusCodes.BadRequest -> "STATUS_MISMATCH" }
              case _ => complete { HttpResponse(StatusCodes.BadRequest) }
            }
          }
        }
      }
    }

  def listTaskSessions(taskId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.ReadTasks, None),
        user
      ) {
        authorizeAsync {
          TaskSessionDao
            .authorized(
              taskId,
              user,
              ActionType.View
            )
            .transact(xa)
            .unsafeToFuture
        } {
          (withPagination) { page =>
            complete {
              TaskSessionDao.query
                .filter(fr"task_id = ${taskId}")
                .page(page)
                .transact(xa)
                .unsafeToFuture()
            }
          }
        }
      }
    }

  def getTaskSession(taskId: UUID, sessionId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.ReadTasks, None),
        user
      ) {
        authorizeAsync {
          TaskSessionDao
            .authorized(
              taskId,
              user,
              ActionType.View
            )
            .transact(xa)
            .unsafeToFuture
        } {
          rejectEmptyResponse {
            complete {
              TaskSessionDao
                .getTaskSessionById(sessionId)
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
    }

  def keepSessionAlive(taskId: UUID, sessionId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.CreateAnnotation, None),
        user
      ) {
        authorizeAsync {
          (
            TaskSessionDao
              .authorized(
                taskId,
                user,
                ActionType.Annotate
              ),
            TaskSessionDao.isOwner(taskId, sessionId, user)
          ).tupled
            .map({ authed =>
              authed._1 && authed._2
            })
            .transact(xa)
            .unsafeToFuture
        } {
          onComplete {
            TaskSessionDao
              .isSessionActive(sessionId)
              .transact(xa)
              .unsafeToFuture
          } {
            case Success(true) =>
              complete {
                TaskSessionDao
                  .keepTaskSessionAlive(sessionId)
                  .transact(xa)
                  .unsafeToFuture
              }
            case Success(false) =>
              complete { StatusCodes.Conflict -> "SESSION_EXPIRED" }
            case _ =>
              complete { HttpResponse(StatusCodes.BadRequest) }

          }
        }
      }
    }

  def completeSession(taskId: UUID, sessionId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.CreateAnnotation, None),
        user
      ) {
        authorizeAsync {
          (
            TaskSessionDao
              .authorized(
                taskId,
                user,
                ActionType.Annotate
              ),
            TaskSessionDao.isOwner(taskId, sessionId, user)
          ).tupled
            .map({ authed =>
              authed._1 && authed._2
            })
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[TaskSession.Complete]) { taskSessionComplete =>
            {
              onComplete {
                (for {
                  isActive <- TaskSessionDao.isSessionActive(sessionId)
                  hasValidStatus <- TaskSessionDao.isToStatusMatchTaskSession(
                    sessionId,
                    taskSessionComplete.toStatus
                  )
                } yield (isActive, hasValidStatus)).transact(xa).unsafeToFuture
              } {
                case Success((true, true)) =>
                  complete {
                    (for {
                      _ <- TaskSessionDao.completeTaskSession(
                        sessionId,
                        taskSessionComplete
                      )
                      prevSession <- TaskSessionDao.getLatestForTask(taskId)
                      _ <- prevSession traverse { session =>
                        AnnotationLabelDao.toggleBySessionId(
                          session.id
                        )
                      }
                      _ <- AnnotationLabelDao.toggleBySessionId(
                        sessionId
                      )
                      taskOpt <- TaskDao.getTaskById(taskId)
                      rowCount <- taskOpt traverse { task =>
                        val taskToUpdate = Task.TaskFeatureCreate(
                          properties = Task.TaskPropertiesCreate(
                            status = taskSessionComplete.toStatus,
                            annotationProjectId = task.annotationProjectId,
                            note = taskSessionComplete.note,
                            taskType = Some(task.taskType),
                            parentTaskId = task.parentTaskId,
                            reviews = Some(task.reviews)
                          ),
                          geometry = task.geometry
                        )
                        TaskDao.updateTask(taskId, taskToUpdate, user)
                      }
                    } yield rowCount).transact(xa).unsafeToFuture
                  }
                case Success((false, _)) =>
                  complete { StatusCodes.Conflict -> "SESSION_EXPIRED" }
                case Success((_, false)) =>
                  complete { StatusCodes.BadRequest -> "STATUS_MISMATCH" }
                case _ =>
                  complete { HttpResponse(StatusCodes.BadRequest) }
              }
            }
          }
        }
      }
    }

  def randomTaskSession: Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.ReadTasks, None),
        user
      ) {
        (withPagination & annotationProjectQueryParameters & taskQueryParameters & parameters(
          'annotationProjectId.as[UUID].?
        )) {
          (page, annotationProjectParams, taskParams, annotationProjectIdOpt) =>
            onComplete {
              TaskSessionDao
                .getRandomTaskSession(
                  user,
                  annotationProjectParams,
                  annotationProjectIdOpt,
                  page.limit,
                  taskParams
                )
                .transact(xa)
                .unsafeToFuture
            } {
              case Success(Some(session)) =>
                complete { session }
              case Success(None) =>
                complete {
                  StatusCodes.BadRequest -> "No matching task to create a session for"
                }
              case Failure(e) =>
                logger.error(e.getMessage)
                complete { HttpResponse(StatusCodes.BadRequest) }
            }
        }
      }
    }

  def listSessionLabels(taskId: UUID, sessionId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.CreateAnnotation, None),
        user
      ) {
        authorizeAsync {
          TaskSessionDao
            .authorized(
              taskId,
              user,
              ActionType.View
            )
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            TaskSessionDao
              .listActiveLabels(sessionId)
              .transact(xa)
              .unsafeToFuture
              .map { labels: List[AnnotationLabelWithClasses] =>
                labels.map(_.toGeoJSONFeature)
              }
          }
        }
      }
    }

  def createLabel(taskId: UUID, sessionId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.CreateAnnotation, None),
        user
      ) {
        authorizeAsync {
          TaskSessionDao
            .authWriteLabelToSession(taskId, sessionId, user)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[AnnotationLabelWithClasses.GeoJSONFeatureCreate]) {
            featureCreate =>
              onSuccess(
                TaskSessionDao
                  .insertLabels(
                    taskId,
                    sessionId,
                    List(featureCreate.toAnnotationLabelWithClassesCreate),
                    user
                  )
                  .transact(xa)
                  .unsafeToFuture
                  .map { labels: List[AnnotationLabelWithClasses] =>
                    // the inserted annotation is just one feature
                    // so we return the first feature from the returned list
                    labels.headOption.map(_.toGeoJSONFeature)
                  }
              ) { createdLabel =>
                complete((StatusCodes.Created, createdLabel))
              }
          }
        }
      }
    }

  def bulkCreateSessionLabels(taskId: UUID, sessionId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.CreateAnnotation, None),
        user
      ) {
        authorizeAsync {
          TaskSessionDao
            .authWriteLabelToSession(taskId, sessionId, user)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[AnnotationLabelWithClassesFeatureCollectionCreate]) { fc =>
            val labelWithClassesCreateList = fc.features map {
              _.toAnnotationLabelWithClassesCreate
            }
            onSuccess(
              TaskSessionDao
                .insertLabels(
                  taskId,
                  sessionId,
                  labelWithClassesCreateList.toList,
                  user
                )
                .transact(xa)
                .unsafeToFuture
                .map { labels: List[AnnotationLabelWithClasses] =>
                  fromSeqToFeatureCollection[
                    AnnotationLabelWithClasses,
                    AnnotationLabelWithClasses.GeoJSON
                  ](
                    labels
                  )
                }
            ) { createdLabels =>
              complete((StatusCodes.Created, createdLabels))
            }
          }
        }
      }
    }

  def bulkUpdateSessionLabels(taskId: UUID, sessionId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.CreateAnnotation, None),
        user
      ) {
        authorizeAsync {
          TaskSessionDao
            .authWriteLabelToSession(taskId, sessionId, user)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[AnnotationLabelWithClassesFeatureCollection]) { fc =>
            val labelWithClassesCreateList = fc.features map {
              _.toAnnotationLabelWithClassesOpt
            }
            onSuccess(
              TaskSessionDao
                .bulkReplaceLabelsInSession(
                  taskId,
                  sessionId,
                  labelWithClassesCreateList.toList.flatten,
                  user
                )
                .transact(xa)
                .unsafeToFuture
                .map { labels: List[AnnotationLabelWithClasses] =>
                  fromSeqToFeatureCollection[
                    AnnotationLabelWithClasses,
                    AnnotationLabelWithClasses.GeoJSON
                  ](
                    labels
                  )
                }
            ) { createdLabels =>
              complete((StatusCodes.Created, createdLabels))
            }
          }
        }
      }
    }

  def getLabel(taskId: UUID, sessionId: UUID, labelId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.CreateAnnotation, None),
        user
      ) {
        authorizeAsync {
          TaskSessionDao
            .authorized(
              taskId,
              user,
              ActionType.View
            )
            .transact(xa)
            .unsafeToFuture
        } {
          rejectEmptyResponse {
            complete {
              TaskSessionDao
                .getLabel(sessionId, labelId)
                .transact(xa)
                .unsafeToFuture
                .map { labelOpt: Option[AnnotationLabelWithClasses] =>
                  labelOpt.map(_.toGeoJSONFeature)
                }
            }
          }
        }
      }
    }

  def updateLabel(taskId: UUID, sessionId: UUID, labelId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.CreateAnnotation, None),
        user
      ) {
        authorizeAsync {
          TaskSessionDao
            .authWriteLabelToSession(taskId, sessionId, user)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[AnnotationLabelWithClasses.GeoJSON]) { feature =>
            onSuccess(
              (feature.toAnnotationLabelWithClassesOpt traverse { label =>
                TaskSessionDao
                  .updateLabel(
                    taskId,
                    sessionId,
                    labelId,
                    label,
                    user
                  )
              }).transact(xa).unsafeToFuture
            ) {
              case Some(updatedLabel) =>
                complete(
                  (StatusCodes.Created, updatedLabel.map(_.toGeoJSONFeature))
                )
              case _ =>
                complete(
                  (StatusCodes.BadRequest -> "NO MATCHING LABEL TO UPDATE")
                )
            }
          }
        }
      }
    }

  def deleteLabel(taskId: UUID, sessionId: UUID, labelId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.CreateAnnotation, None),
        user
      ) {
        authorizeAsync {
          TaskSessionDao
            .authWriteLabelToSession(
              taskId,
              sessionId,
              user
            )
            .transact(xa)
            .unsafeToFuture
        } {
          onComplete {
            TaskSessionDao
              .deleteLabel(taskId, sessionId, labelId)
              .transact(xa)
              .unsafeToFuture
          } {
            case Success(rowCount) =>
              if (rowCount == -1) complete {
                StatusCodes.BadRequest -> "NO MATCHING LABEL TO DELETE"
              } else complete { StatusCodes.OK }
            case Failure(e) =>
              logger.error(e.getMessage)
              complete {
                StatusCodes.BadRequest -> "ERROR IN LABEL DELETE"
              }
          }
        }
      }
    }
}
