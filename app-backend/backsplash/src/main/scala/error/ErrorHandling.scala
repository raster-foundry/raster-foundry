package com.rasterfoundry.backsplash.error

import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.datamodel.User

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
final case class WrappedDoobieException(message: String)
    extends BacksplashException
final case class WrappedS3Exception(message: String) extends BacksplashException
// Private so no one can deliberately throw an UnknownException elsewhere --
// only exists to catch the fall-through case in the foreign error handler
private[error] final case class UnknownException(message: String)
    extends BacksplashException

class ForeignErrorHandler[F[_], E <: Throwable](implicit M: MonadError[F, E])
    extends RollbarNotifier
    with HttpErrorHandler[F, E]
    with Http4sDsl[F] {
  private def wrapError(t: E): F[Response[F]] = t match {
    case (err: InvariantViolation) =>
      logger.error(err.getMessage, err.printStackTrace)
      throw WrappedDoobieException(err.getMessage)
    case (err: AmazonS3Exception) =>
      logger.error(err.getMessage, err.printStackTrace)
      throw WrappedS3Exception(err.getMessage)
    case (err: BacksplashException) => throw err
    case t                          => throw UnknownException(t.getMessage)
  }

  override def handle(service: AuthedService[User, F]): AuthedService[User, F] =
    ServiceHttpErrorHandler(service)(wrapError)
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
        "Resource does not exist or user is not authorized to access this resource")
    case WrappedDoobieException(m) =>
      NotFound(m)
    case WrappedS3Exception(_) =>
      NotFound(
        "Underlying data to produce tiles for this project appears to have moved or is no longer available")
    case t @ UnknownException(m) =>
      sendError(t)
      InternalServerError(m)
  }

  override def handle(service: AuthedService[User, F]): AuthedService[User, F] =
    ServiceHttpErrorHandler(service)(handler)
}

object ServiceHttpErrorHandler {
  def apply[F[_], E](service: AuthedService[User, F])(
      handler: E => F[Response[F]])(
      implicit ev: ApplicativeError[F, E]): AuthedService[User, F] =
    Kleisli { req: AuthedRequest[F, User] =>
      OptionT {
        service(req).value.handleErrorWith { e =>
          handler(e).map(Option(_))
        }
      }
    }
}

trait HttpErrorHandler[F[_], E <: Throwable] extends RollbarNotifier {
  def handle(service: AuthedService[User, F]): AuthedService[User, F]
}

object HttpErrorHandler {
  def apply[F[_], E <: Throwable](implicit ev: HttpErrorHandler[F, E]) = ev
}
