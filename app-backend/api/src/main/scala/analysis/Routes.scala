package com.azavea.rf.api.analysis

import com.azavea.rf.common._
import com.azavea.rf.common.ast._
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast.MapAlgebraAST
import com.azavea.rf.tool.eval.PureInterpreter
import com.azavea.rf.database.filter.Filterables._
import com.azavea.maml.serve.InterpreterExceptionHandling
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.implicits._
import java.util.UUID

import cats.effect.IO
import com.azavea.rf.database.AnalysisDao
import doobie.util.transactor.Transactor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._



trait AnalysisRoutes extends Authentication
    with PaginationDirectives
    with AnalysisQueryParametersDirective
    with CommonHandlers
    with UserErrorHandler
    with InterpreterExceptionHandling {

  val xa: Transactor[IO]

  val analysisRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listAnalyses } ~
      post { createAnalysis }
    } ~
    pathPrefix(JavaUUID) { analysisId =>
      pathEndOrSingleSlash {
        get { getAnalysis(analysisId) } ~
        put { updateAnalysis(analysisId) } ~
        delete { deleteAnalysis(analysisId) }
      }
    }
  }

  def listAnalyses: Route = authenticate { user =>
    (withPagination & analysisQueryParameters) { (page, analysisParams) =>
      complete {
        AnalysisDao.query.filter(analysisParams).ownerFilter(user).page(page).transact(xa).unsafeToFuture
      }
    }
  }

  def createAnalysis: Route = authenticate { user =>
    entity(as[Analysis.Create]) { newAnalysis =>
      authorize(user.isInRootOrSameOrganizationAs(newAnalysis)) {
        onComplete(AnalysisDao.insertAnalysis(newAnalysis, user).transact(xa).unsafeToFuture) {
          case Success(analysis) =>
            handleExceptions(interpreterExceptionHandler) {
              complete {
                (StatusCodes.Created, analysis)
              }
            }
          case Failure(error) => complete(StatusCodes.ClientError(400)("Error creating analysis", error.getMessage()))
        }
      }
    }
  }

  def getAnalysis(analysisId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete(AnalysisDao.query.filter(analysisId).selectOption.transact(xa).unsafeToFuture)
    }
  }

  def updateAnalysis(analysisId: UUID): Route = authenticate { user =>
    entity(as[Analysis]) { updatedAnalysis =>
      authorize(user.isInRootOrSameOrganizationAs(updatedAnalysis)) {
        onComplete(AnalysisDao.updateAnalysis(updatedAnalysis, analysisId, user).transact(xa).unsafeToFuture) {
          case Success(count) => completeSingleOrNotFound(count)
          case Failure(_) => complete(StatusCodes.NotFound)
        }
      }
    }
  }

  def deleteAnalysis(analysisId: UUID): Route = authenticate { user =>
    onComplete(AnalysisDao.query.filter(analysisId).ownerFilter(user).delete.transact(xa).unsafeToFuture) {
      case Success(x) =>
        completeSingleOrNotFound(x)
      case Failure(_) => complete(StatusCodes.NotFound)

    }
  }
}
