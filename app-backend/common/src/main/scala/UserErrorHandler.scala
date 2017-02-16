package com.azavea.rf.common

import akka.http.scaladsl.server.{Route, ExceptionHandler, Directives}
import akka.http.scaladsl.model.StatusCodes
import com.typesafe.scalalogging.LazyLogging
import spray.json.{SerializationException, DeserializationException}
import com.typesafe.scalalogging.LazyLogging

trait UserErrorHandler extends Directives
    with RollbarNotifier
    with LazyLogging {
  val userExceptionHandler = ExceptionHandler {
    case e: IllegalArgumentException =>
      logger.error(RfStackTrace(e))
      sendError(e)
      complete(StatusCodes.ClientError(400)("Bad Argument", e.getMessage))
    case e: IllegalStateException =>
      logger.error(RfStackTrace(e))
      sendError(e)
      complete(StatusCodes.ClientError(400)("Bad Request", e.getMessage))
    case e: DeserializationException =>
      logger.error(RfStackTrace(e))
      sendError(e)
      complete(StatusCodes.ClientError(400)("Decoding Error", e.getMessage))
    case e: SerializationException =>
      logger.error(RfStackTrace(e))
      sendError(e)
      complete(StatusCodes.ServerError(500)("Encoding Error", e.getMessage))
    case e: Exception =>
      sendError(e)
      complete(StatusCodes.ServerError(501)("An unknown error occurred", ""))
  }
}
