package com.azavea.rf.common

import akka.http.scaladsl.server.{ExceptionHandler, Directives}
import akka.http.scaladsl.model.{IllegalRequestException, StatusCodes}
import com.typesafe.scalalogging.LazyLogging
import java.security.InvalidParameterException
import org.postgresql.util.PSQLException

trait UserErrorHandler
    extends Directives
    with RollbarNotifier
    with LazyLogging {
  val userExceptionHandler = ExceptionHandler {
    case e: PSQLException if (e.getSQLState == "23505") =>
      complete(StatusCodes.ClientError(409)("Duplicate Key", ""))
    case e: InvalidParameterException =>
      logger.error(RfStackTrace(e))
      complete(StatusCodes.ClientError(400)("Bad Request", e.getMessage))
    case e: IllegalArgumentException =>
      logger.error(RfStackTrace(e))
      complete(StatusCodes.ClientError(400)("Bad Argument", e.getMessage))
    case e: IllegalStateException =>
      logger.error(RfStackTrace(e))
      complete(StatusCodes.ClientError(400)("Bad Request", e.getMessage))
    case e: IllegalRequestException =>
      logger.error(RfStackTrace(e))
      // Status code and error message are expected to be contained within
      complete(e)
    case e: Exception =>
      logger.error(RfStackTrace(e))
      sendError(e)
      complete(StatusCodes.ServerError(501)("An unknown error occurred", ""))
  }
}
