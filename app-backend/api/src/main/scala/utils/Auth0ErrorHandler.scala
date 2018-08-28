package com.azavea.rf.api.utils

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, ExceptionHandler}
import com.azavea.rf.common.RfStackTrace
import com.typesafe.scalalogging.LazyLogging

trait Auth0ErrorHandler extends Directives with LazyLogging {

  val auth0ExceptionHandler = ExceptionHandler {
    case e: Auth0Exception =>
      logger.error(RfStackTrace(e))
      complete(
        StatusCodes.ServerError(500)("Authentication Service Error",
                                     e.getClientMessage))
  }
}
