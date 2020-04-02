package com.rasterfoundry.api.tasks

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import cats.implicits._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.implicits._

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
    }
  }

  def listTasks: Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.AnnotationProjects, Action.ReadTasks, None),
      user
    ) {
      (withPagination & annotationProjectQueryParameters & taskQueryParameters & parameters(
        'annotationProjectId.as[UUID].?
      )) { (page, annotationProjectParams, taskParams, annotationProjectId) =>
        onComplete {
          (for {
            annotationProjects <- AnnotationProjectDao
              .authQuery(user, ObjectType.AnnotationProject, None, None, None)
              .filter(annotationProjectParams)
              .filter(annotationProjectId)
              .list(page.limit) map { projects =>
              projects map { _.id }
            }
            taskO <- annotationProjects.toNel traverse { projectIds =>
              TaskDao
                .randomTask(taskParams, projectIds)
            }
          } yield {
            taskO.flatten
          }).transact(xa).unsafeToFuture
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
}
