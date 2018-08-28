package com.azavea.rf.api.utils

import akka.http.scaladsl.model.StatusCode

class Auth0Exception(code: StatusCode, message: String, cause: Throwable)
    extends RuntimeException(Auth0Exception.defaultMessage(message, cause),
                             cause) {

  @SuppressWarnings(Array("NullParameter"))
  def this(code: StatusCode, message: String) = this(code, message, null)

  def getClientMessage: String = s"Authentication service returned error $code"
}

object Auth0Exception {
  @SuppressWarnings(Array("NullParameter"))
  def defaultMessage(message: String, cause: Throwable): String = {
    if (message != null) message
    else if (cause != null) cause.toString
    else "Unknown error communicating with authentication service"
  }
}
