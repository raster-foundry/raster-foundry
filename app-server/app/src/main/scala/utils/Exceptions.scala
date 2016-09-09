package com.azavea.rf.utils

import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives

// ignore warnings - can't adapt exceptions to not use nulls as possible args
class UserErrorException(message: String, cause: Throwable)
    extends RuntimeException(UserErrorException.defaultMessage(message, cause), cause) {
  def this(message: String) = {
    this(message, null)
  }
}


object UserErrorException {
  def defaultMessage(message: String, cause: Throwable) =
    if (message != null) message
    else if (cause != null) cause.toString()
    else "Unknown User Error"
}


trait UserErrorHandler extends Directives {
  val userExceptionHandler = ExceptionHandler {
    case e: UserErrorException =>
      complete(StatusCodes.ClientError(400)("", e.getMessage()))
  }
}
