package com.azavea.rf.healthcheck

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import StatusCodes._

import org.postgresql.util.PSQLException

import com.azavea.rf.database.Database
import com.azavea.rf.auth.Authentication

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
