package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.common.RollbarNotifier

import cats._
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.typesafe.scalalogging.LazyLogging
import doobie.util.invariant.InvariantViolation
import org.http4s._
import org.http4s.dsl._

class ForeignErrorHandler[F[_], E <: Throwable](implicit M: MonadError[F, E])
    extends LazyLogging
    with HttpErrorHandler[F, E]
    with Http4sDsl[F] {
  private def wrapError(req: Request[F], t: E): F[Response[F]] = {
    t match {
      case (err: InvariantViolation) =>
        logger.error(
          s"Exception Retrieving ${req.uri.path}, TraceID: ${req.traceID} ${err.getMessage}",
          err)
        throw WrappedDoobieException(err.getMessage)
      case (err: AmazonS3Exception) =>
        logger.error(
          s"Exception Retrieving ${req.uri.path}, TraceID: ${req.traceID} ${err.getMessage}",
          err)
        throw WrappedS3Exception(err.getMessage)
      case (err: IllegalArgumentException) => {
        logger.error(
          s"Exception Retrieving ${req.uri.path}, TraceID: ${req.traceID} ${err.getMessage}",
          err)
        throw RequirementFailedException(err.getMessage)
      }
      case (err: BacksplashException) =>
        logger.error(
          s"Exception Retrieving ${req.uri.path}, TraceID: ${req.traceID} ${err.getMessage}",
          err)
        throw err
      case t =>
        logger.error(
          s"An unmapped error occurred retrieving ${req.uri.path}, TraceID: ${req.traceID}",
          t)
        throw UnknownException(t.getMessage)
    }
  }

  override def handle(service: HttpRoutes[F]): HttpRoutes[F] =
    ServiceHttpErrorHandler(service)(wrapError)
}

class RollbarReporter[F[_]](implicit M: MonadError[F, BacksplashException])
    extends RollbarNotifier
    with HttpErrorHandler[F, BacksplashException] {
  private def wrapError(r: Request[F],
                        t: BacksplashException): F[Response[F]] = {
    t match {
      case e @ UningestedScenesException(_) =>
        sendError(e, r.traceID, r.uri.path)
        throw e
      case e @ NoScenesException =>
        throw e
      case e @ MetadataException(_) =>
        sendError(e, r.traceID, r.uri.path)
        throw e
      case e @ SingleBandOptionsException(_) =>
        sendError(e, r.traceID, r.uri.path)
        throw e
      case e @ UnknownSceneTypeException(_) =>
        sendError(e, r.traceID, r.uri.path)
        throw e
      case e @ BadAnalysisASTException(_) =>
        sendError(e, r.traceID, r.uri.path)
        throw e
      case e @ UnknownException(_) =>
        sendError(e, r.traceID, r.uri.path)
        throw e
      case e =>
        throw e
    }
  }

  override def handle(service: HttpRoutes[F]): HttpRoutes[F] =
    ServiceHttpErrorHandler(service)(wrapError)
}
