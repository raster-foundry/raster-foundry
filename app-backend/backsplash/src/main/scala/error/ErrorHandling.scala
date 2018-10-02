package com.azavea.rf.backsplash.error

import com.azavea.rf.common.RollbarNotifier

import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import com.amazonaws.services.s3.model.AmazonS3Exception
import org.http4s._
import org.http4s.dsl._
import org.http4s.dsl.io._
import org.http4s.implicits._
import com.olegpy.meow.hierarchy._

sealed trait BacksplashError extends Exception
case class MetadataError(message: String) extends BacksplashError
case class SingleBandOptionsError(message: String) extends BacksplashError
case class UningestedScenes(message: String) extends BacksplashError
case class UnknownSceneType(message: String) extends BacksplashError
case class NotAuthorized(message: String = "") extends BacksplashError

class BacksplashHttpErrorHandler[F[_]](implicit M: MonadError[F, BacksplashError]) extends HttpErrorHandler[F, BacksplashError] with Http4sDsl[F] with RollbarNotifier {
  private val handler: BacksplashError => F[Response[F]] = {
    case t @ MetadataError(m) =>
      sendError(t)
      InternalServerError(m)
    case SingleBandOptionsError(m) => BadRequest(m)
    case UningestedScenes(m)       => NotFound(m)
    case UnknownSceneType(m)       => BadRequest(m)
    case NotAuthorized(_) =>
      Forbidden(
        "Tiles cannot be produced or user is not authorized to view these tiles")
  }

  override def handle(service: HttpService[F]): HttpService[F] =
    ServiceHttpErrorHandler(service)(handler)
}

object ServiceHttpErrorHandler {
  def apply[F[_], E](service: HttpService[F])(handler: E => F[Response[F]])(implicit ev: ApplicativeError[F, E]): HttpService[F] =
    Kleisli { req: Request[F] =>
      OptionT {
        service(req).value.handleErrorWith { e => handler(e).map(Option(_)) }
      }
    }
}

trait HttpErrorHandler[F[_], E <: Throwable] extends RollbarNotifier {
  def handle(service: HttpService[F]): HttpService[F]
}

object HttpErrorHandler {
  def apply[F[_], E <: Throwable](implicit ev: HttpErrorHandler[F, E]) = ev
}
