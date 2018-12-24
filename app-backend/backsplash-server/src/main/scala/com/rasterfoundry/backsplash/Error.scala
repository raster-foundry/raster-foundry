package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.error._

import cats._
import com.typesafe.scalalogging.LazyLogging
import doobie.util.invariant.InvariantViolation
import org.http4s._
import org.http4s.dsl._
import org.http4s.dsl.io._
import java.lang.{IllegalArgumentException, NullPointerException}

class ForeignErrorHandler[F[_], E <: Throwable, U](implicit M: MonadError[F, E])
    extends LazyLogging
    with HttpErrorHandler[F, E, U]
    with Http4sDsl[F] {
  private def wrapError(t: E): F[Response[F]] = t match {
    case (err: InvariantViolation) =>
      logger.error(err.getMessage, err.printStackTrace)
      throw WrappedDoobieException(err.getMessage)
    case (err: NullPointerException) =>
      logger.error(err.getMessage, err.printStackTrace)
      throw MissingSceneDataException(err.getMessage)
    case (err: IllegalArgumentException) =>
      throw RequirementFailedException(err.getMessage)
    case (err: BacksplashException) => throw err
    case t                          => throw UnknownException(t.getMessage)
  }

  override def handle(service: AuthedService[U, F]): AuthedService[U, F] =
    ServiceHttpErrorHandler(service)(wrapError)
}
