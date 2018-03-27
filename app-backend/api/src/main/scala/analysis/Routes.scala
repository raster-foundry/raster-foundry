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
        onSuccess(AnalysisDao.insertAnalysis(newAnalysis, user).transact(xa).unsafeToFuture) { analysis =>
          handleExceptions(interpreterExceptionHandler) {
            complete {
              (StatusCodes.Created, analysis)
            }
          }
        }
      }
    }
  }

  def getAnalysis(analysisId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete(AnalysisDao.query.filter(fr"id = ${analysisId}").selectOption.transact(xa).unsafeToFuture)
    }
  }

  def updateAnalysis(analysisId: UUID): Route = authenticate { user =>
    entity(as[Analysis]) { updatedAnalysis =>
      authorize(user.isInRootOrSameOrganizationAs(updatedAnalysis)) {
        onSuccess(AnalysisDao.updateAnalysis(updatedAnalysis, analysisId, user).transact(xa).unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteAnalysis(analysisId: UUID): Route = authenticate { user =>
    onSuccess(AnalysisDao.query.filter(fr"id = ${analysisId}").ownerFilter(user).delete.transact(xa).unsafeToFuture) {
      completeSingleOrNotFound
    }
  }
}
