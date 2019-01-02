package com.rasterfoundry.api.healthcheck

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server._
import cats.effect.IO
import com.rasterfoundry.akkautil.Authentication
import com.rasterfoundry.api.Codec._
import com.rasterfoundry.common.RollbarNotifier
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.util.transactor.Transactor
import org.postgresql.util.PSQLException
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._

/**
  * Routes for healthchecks -- additional routes for individual healthchecks
  * should be included here as well
  *
  */
trait HealthCheckRoutes extends Authentication with RollbarNotifier {

  val xa: Transactor[IO]

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
          HealthCheckService.healthCheck.transact(xa).unsafeToFuture
        }
      }
    }
  }
}
