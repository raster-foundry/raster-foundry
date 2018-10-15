package com.rasterfoundry.backsplash.error

import com.rasterfoundry.common.RollbarNotifier

import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import com.amazonaws.services.s3.model.AmazonS3Exception
import doobie.util.invariant.InvariantViolation
import org.http4s._
import org.http4s.dsl._
import org.http4s.dsl.io._
import org.http4s.implicits._

sealed trait BacksplashException extends Exception
final case class MetadataException(message: String) extends BacksplashException
final case class SingleBandOptionsException(message: String)
    extends BacksplashException
final case class UningestedScenesException(message: String)
    extends BacksplashException
final case class UnknownSceneTypeException(message: String)
    extends BacksplashException
final case class NotAuthorizedException(message: String = "")
    extends BacksplashException
final case class BadAnalysisASTException(message: String)
    extends BacksplashException

trait ErrorHandling extends RollbarNotifier {
  def handleErrors(t: Throwable): IO[Response[IO]] = t match {
    case t @ MetadataException(m) =>
      sendError(t)
      InternalServerError(m)
    case SingleBandOptionsException(m) => BadRequest(m)
    case UningestedScenesException(m)  => NotFound(m)
    case UnknownSceneTypeException(m)  => BadRequest(m)
    case BadAnalysisASTException(m)    => BadRequest(m)
    case NotAuthorizedException(_) =>
      sendError(t)
      Forbidden(
        "Resource does not exist or user is not authorized to access resource")
    case (err: InvariantViolation) =>
      logger.error(err.getMessage, err.printStackTrace)
      NotFound("Necessary data to produce tiles not available")
    case (err: AmazonS3Exception) =>
      logger.error(err.getMessage, err.printStackTrace)
      NotFound(
        "Necessary data to produce tiles not available. Check to ensure underlying data are still accessible in S3")
    case err => InternalServerError(err.getMessage)
  }
}

class BacksplashHttpErrorHandler[F[_]](
    implicit M: MonadError[F, BacksplashException])
    extends HttpErrorHandler[F, BacksplashException]
    with Http4sDsl[F]
    with RollbarNotifier {
  private val handler: BacksplashException => F[Response[F]] = {
    case t @ MetadataException(m) =>
      sendError(t)
      InternalServerError(m)
    case SingleBandOptionsException(m) => BadRequest(m)
    case UningestedScenesException(m)  => NotFound(m)
    case UnknownSceneTypeException(m)  => BadRequest(m)
    case BadAnalysisASTException(m)    => BadRequest(m)
    case t @ NotAuthorizedException(_) =>
      sendError(t)
      Forbidden(
        "Resource does not exist or user is not authorized to access resource")
  }

  override def handle(service: HttpRoutes[F]): HttpRoutes[F] =
    ServiceHttpErrorHandler(service)(handler)
}

object ServiceHttpErrorHandler {
  def apply[F[_], E](service: HttpRoutes[F])(handler: E => F[Response[F]])(
      implicit ev: ApplicativeError[F, E]): HttpRoutes[F] =
    Kleisli { req: Request[F] =>
      OptionT {
        service(req).value.handleErrorWith { e =>
          handler(e).map(Option(_))
        }
      }
    }
}

trait HttpErrorHandler[F[_], E <: Throwable] extends RollbarNotifier {
  def handle(service: HttpRoutes[F]): HttpRoutes[F]
}

object HttpErrorHandler {
  def apply[F[_], E <: Throwable](implicit ev: HttpErrorHandler[F, E]) = ev
}
