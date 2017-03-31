package com.azavea.rf.common

import akka.http.scaladsl.server.{Route, ExceptionHandler, Directives}
import akka.http.scaladsl.model.StatusCodes
import com.typesafe.scalalogging.LazyLogging
import java.security.InvalidParameterException
import com.typesafe.scalalogging.LazyLogging
import org.postgresql.util.PSQLException


trait UserErrorHandler extends Directives
    with RollbarNotifier
    with LazyLogging {
  val userExceptionHandler = ExceptionHandler {
    case e: PSQLException if(e.getSQLState == "23505") =>
      complete(StatusCodes.ClientError(409)("Duplicate Key", ""))
    case e: IllegalArgumentException =>
      logger.error(RfStackTrace(e))
      complete(StatusCodes.ClientError(400)("Bad Argument", e.getMessage))
    case e: IllegalStateException =>
      logger.error(RfStackTrace(e))
      complete(StatusCodes.ClientError(400)("Bad Request", e.getMessage))
    case e: InvalidParameterException =>
      logger.error(RfStackTrace(e))
      complete(StatusCodes.ClientError(400)("Bad Request", e.getMessage))
    case e: Exception =>
      sendError(e)
      complete(StatusCodes.ServerError(501)("An unknown error occurred", ""))
  }
}
