package com.rasterfoundry.api.annotationProject

import com.rasterfoundry.akkautil.{Authentication, CommonHandlers}
import com.rasterfoundry.database.AnnotationProjectDao
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.effect.IO
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.util.transactor.Transactor
import doobie._
import doobie.implicits._

trait AnnotationProjectRoutes extends CommonHandlers with Authentication {

  val xa: Transactor[IO]

  val annotationProjectRoutes: Route = {
    pathEndOrSingleSlash {
      post {
        createAnnotationProject
      }
    }
  }

  def createAnnotationProject: Route = authenticate { user =>
    authorizeScopeLimit(
      AnnotationProjectDao.countUserProjects(user).transact(xa).unsafeToFuture,
      Domain.Projects,
      Action.Create,
      user
    ) {
      entity(as[AnnotationProject.Create]) { newAnnotationProject =>
        onSuccess(
          AnnotationProjectDao
            .insertAnnotationProject(newAnnotationProject, user)
            .transact(xa)
            .unsafeToFuture
        ) { annotationProject =>
          complete((StatusCodes.Created, annotationProject))
        }
      }
    }
  }
}
