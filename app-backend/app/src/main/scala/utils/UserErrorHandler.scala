package com.azavea.rf.utils

import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import spray.json.{SerializationException, DeserializationException}

trait UserErrorHandler extends Directives {
  val userExceptionHandler = ExceptionHandler {
    case e: IllegalArgumentException =>
      complete(StatusCodes.ClientError(400)("Bad Argument", e.getMessage))
    case e: IllegalStateException =>
      complete(StatusCodes.ClientError(400)("Bad Request", e.getMessage))
    case e: DeserializationException =>
      complete(StatusCodes.ClientError(400)("Decoding Error", e.getMessage))
    case e: SerializationException =>
      complete(StatusCodes.ServerError(500)("Encoding Error", e.getMessage))
  }
}