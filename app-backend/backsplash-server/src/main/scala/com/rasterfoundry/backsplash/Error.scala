package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.error._

import cats._
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.typesafe.scalalogging.LazyLogging
import doobie.util.invariant.InvariantViolation
import org.http4s._
import org.http4s.dsl._
import org.http4s.dsl.io._
import java.lang.IllegalArgumentException

class ForeignErrorHandler[F[_], E <: Throwable, U](implicit M: MonadError[F, E])
    extends LazyLogging
    with HttpErrorHandler[F, E, U]
    with Http4sDsl[F] {
  private def wrapError(t: E): F[Response[F]] = t match {
    case (err: InvariantViolation) =>
      logger.error(err.getMessage, err)
      throw WrappedDoobieException(err.getMessage)
    case (err: AmazonS3Exception) =>
      logger.error(err.getMessage, err)
      throw WrappedS3Exception(err.getMessage)
    case (err: IllegalArgumentException) => {
      logger.error(err.getMessage, err)
      throw RequirementFailedException(err.getMessage)
    }
    case (err: BacksplashException) =>
      throw {
        logger.error(err.getMessage, err)
        err
      }
    case t => {
      logger.error("An unmapped error occurred", t)
      throw UnknownException(t.getMessage)
    }
  }

  override def handle(service: AuthedService[U, F]): AuthedService[U, F] =
    ServiceHttpErrorHandler(service)(wrapError)
}
