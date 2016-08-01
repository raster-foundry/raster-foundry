package com.azavea.rf.healthcheck

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import StatusCodes._

import org.postgresql.util.PSQLException

import com.azavea.rf.utils.Database


/**
  * Routes for healthchecks -- additional routes for individual healthchecks
  * should be included here as well
  * 
  */
trait HealthCheckRoutes {

  val healthCheckExceptionHandler = ExceptionHandler {
    case e: PSQLException =>
      extractUri { uri =>
        val dbCheck = ServiceCheck("database", HealthCheckStatus.Failing)
        val healthCheck = HealthCheck(HealthCheckStatus.Failing, Seq(dbCheck))
        complete((InternalServerError, healthCheck))
      }
  }

  def healthCheckRoutes()(implicit db:Database, ec:ExecutionContext) = (
    handleExceptions(healthCheckExceptionHandler){
      pathPrefix("healthcheck") {
        pathEndOrSingleSlash {
          onSuccess(HealthCheckService.healthCheck) { resp =>
            complete(resp)
          }
        }
      }
    }

  )

}
