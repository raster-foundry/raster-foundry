package com.azavea.rf.utils

import akka.http.scaladsl.server.{Route, ExceptionHandler, Directives}
import akka.http.scaladsl.model.StatusCodes
import com.typesafe.scalalogging.LazyLogging
import spray.json.{SerializationException, DeserializationException}

trait UserErrorHandler extends Directives with LazyLogging {
  val userExceptionHandler = ExceptionHandler {
    case e: IllegalArgumentException =>
      logger.error(RfStackTrace(e))
      complete(StatusCodes.ClientError(400)("Bad Argument", e.getMessage))
    case e: IllegalStateException =>
      logger.error(RfStackTrace(e))
      complete(StatusCodes.ClientError(400)("Bad Request", e.getMessage))
    case e: DeserializationException =>
      logger.error(RfStackTrace(e))
      complete(StatusCodes.ClientError(400)("Decoding Error", e.getMessage))
    case e: SerializationException =>
      logger.error(RfStackTrace(e))
      complete(StatusCodes.ServerError(500)("Encoding Error", e.getMessage))
  }
}