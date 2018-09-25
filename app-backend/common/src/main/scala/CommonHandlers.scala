package com.azavea.rf.common

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{RequestContext, RouteResult}
import akka.http.scaladsl.server.{StandardRoute, ExceptionHandler}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.{
  RouteDirectives,
  FutureDirectives,
  CompleteOrRecoverWithMagnet
}
import io.circe._
import cats.syntax.show

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait CommonHandlers extends RouteDirectives {
  def completeWithOneOrFail(
      future: ⇒ Future[Int]
  ): RequestContext => Future[RouteResult] =
    FutureDirectives.onComplete(future) {
      case Success(count) => completeSingleOrNotFound(count)
      case Failure(err)   => failWith(err)
    }

  def completeOrFail(
      magnet: CompleteOrRecoverWithMagnet
  ): RequestContext => Future[RouteResult] =
    FutureDirectives.completeOrRecoverWith(magnet)(failWith)

  def completeSingleOrNotFound(count: Int): StandardRoute = count match {
    case 1 => complete(StatusCodes.NoContent)
    case 0 => complete(StatusCodes.NotFound)
    case _ =>
      throw new IllegalStateException(s"Result expected to be 1, was $count")
  }

  def completeSomeOrNotFound(count: Int): StandardRoute = count match {
    case 0          => complete(StatusCodes.NotFound)
    case x if x > 0 => complete(StatusCodes.NoContent)
    case _ =>
      throw new IllegalStateException(
        s"Result expected to be 0 or positive, was $count")
  }

  def circeDecodingError = ExceptionHandler {
    case df: DecodingFailure =>
      complete {
        (StatusCodes.BadRequest, DecodingFailure.showDecodingFailure.show(df))
      }
  }
}
