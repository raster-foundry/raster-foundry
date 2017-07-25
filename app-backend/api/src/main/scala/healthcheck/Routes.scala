package com.azavea.rf.api.healthcheck

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server._
import com.azavea.rf.api.Codec._
import com.azavea.rf.common.{Authentication, RollbarNotifier}
import com.azavea.rf.database.Database
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import org.postgresql.util.PSQLException

/**
  * Routes for healthchecks -- additional routes for individual healthchecks
  * should be included here as well
  *
  */
trait HealthCheckRoutes extends Authentication with RollbarNotifier {

  implicit def database: Database

  val healthCheckExceptionHandler = ExceptionHandler {
    case e: PSQLException =>
      sendError(e)
      extractUri { uri =>
        val dbCheck = ServiceCheck("database", HealthCheckStatus.Failing)
        val healthCheck = HealthCheck(HealthCheckStatus.Failing, Seq(dbCheck))
        complete((InternalServerError, healthCheck))
      }
    case e: Exception =>
      sendError(e)
      complete(InternalServerError)
  }

  val healthCheckRoutes = handleExceptions(healthCheckExceptionHandler) {
    pathEndOrSingleSlash {
      get {
        complete {
          HealthCheckService.healthCheck
        }
      }
    }
  }
}
