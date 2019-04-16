package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.common.RollbarNotifier

import cats._
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.typesafe.scalalogging.LazyLogging
import doobie.util.invariant.InvariantViolation
import org.http4s._
import org.http4s.dsl._
import java.lang.IllegalArgumentException

class ForeignErrorHandler[F[_], E <: Throwable](implicit M: MonadError[F, E])
    extends LazyLogging
    with HttpErrorHandler[F, E]
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

  override def handle(service: HttpRoutes[F]): HttpRoutes[F] =
    ServiceHttpErrorHandler(service)(wrapError)
}

class RollbarReporter[F[_]](implicit M: MonadError[F, BacksplashException])
    extends RollbarNotifier
    with HttpErrorHandler[F, BacksplashException] {
  private def wrapError(t: BacksplashException): F[Response[F]] = t match {
    case e @ UningestedScenesException(_) =>
      sendError(e)
      throw e
    case e @ MetadataException(_) =>
      sendError(e)
      throw e
    case e @ SingleBandOptionsException(_) =>
      sendError(e)
      throw e
    case e @ UnknownSceneTypeException(_) =>
      sendError(e)
      throw e
    case e @ BadAnalysisASTException(_) =>
      sendError(e)
      throw e
    case e @ UnknownException(_) =>
      sendError(e)
      throw e
    case e =>
      throw e
  }

  override def handle(service: HttpRoutes[F]): HttpRoutes[F] =
    ServiceHttpErrorHandler(service)(wrapError)
}
