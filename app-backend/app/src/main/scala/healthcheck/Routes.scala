package com.azavea.rf.healthcheck

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import StatusCodes._

import org.postgresql.util.PSQLException

import com.azavea.rf.utils.Database
import com.azavea.rf.auth.Authentication

/**
  * Routes for healthchecks -- additional routes for individual healthchecks
  * should be included here as well
  * 
  */
trait HealthCheckRoutes extends Authentication {

  implicit def database: Database
  implicit val ec: ExecutionContext

  val healthCheckExceptionHandler = ExceptionHandler {
    case e: PSQLException =>
      extractUri { uri =>
        val dbCheck = ServiceCheck("database", HealthCheckStatus.Failing)
        val healthCheck = HealthCheck(HealthCheckStatus.Failing, Seq(dbCheck))
        complete((InternalServerError, healthCheck))
      }
  }

  val healthCheckRoutes = (
    handleExceptions(healthCheckExceptionHandler) {
      authenticateAndAllowAnonymous { user =>
        pathPrefix("healthcheck") {
          pathEndOrSingleSlash {
            onSuccess(HealthCheckService.healthCheck) { resp =>
              complete(resp)
            }
          }
        }
      }
    }
  )
}
