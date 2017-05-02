package com.azavea.rf.common

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{StandardRoute, ExceptionHandler}
import akka.http.scaladsl.server.directives.RouteDirectives
import io.circe._
import cats.syntax.show


trait CommonHandlers extends RouteDirectives {
  def completeSingleOrNotFound(count: Int): StandardRoute = count match {
    case 1 => complete(StatusCodes.NoContent)
    case 0 => complete(StatusCodes.NotFound)
    case _ => throw new IllegalStateException(s"Result expected to be 1, was $count")
  }

  def circeDecodingError = ExceptionHandler {
    case df: DecodingFailure => complete { (StatusCodes.InternalServerError, DecodingFailure.showDecodingFailure.show(df)) }
  }
}
