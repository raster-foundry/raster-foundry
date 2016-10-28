package com.azavea.rf.healthcheck

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server._

import org.postgresql.util.PSQLException

import com.azavea.rf.auth.Authentication
import com.azavea.rf.database.Database


/**
  * Routes for healthchecks -- additional routes for individual healthchecks
  * should be included here as well
  * 
  */
trait HealthCheckRoutes extends Authentication {

  implicit def database: Database

  val healthCheckExceptionHandler = ExceptionHandler {
    case e: PSQLException =>
      extractUri { uri =>
        val dbCheck = ServiceCheck("database", HealthCheckStatus.Failing)
        val healthCheck = HealthCheck(HealthCheckStatus.Failing, Seq(dbCheck))
        complete((InternalServerError, healthCheck))
      }
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
