package com.azavea.rf.backsplash.error

import com.azavea.rf.common.RollbarNotifier

import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.nimbusds.jwt.proc.BadJWTException
import doobie.util.invariant.UnexpectedEnd
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

class BacksplashHttpErrorHandler extends HttpErrorHandler[BacksplashError] with Http4sDsl[IO] with RollbarNotifier {
  private val handler: BacksplashError => IO[Response[IO]] = {
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

  override def handle(service: HttpService[IO]): HttpService[IO] =
    ServiceHttpErrorHandler(service)(handler)
}

object ServiceHttpErrorHandler {
  def apply[E <: Throwable](service: HttpService[IO])(handler: E => IO[Response[IO]]): HttpService[IO] =
    Kleisli { req: Request[IO] =>
      OptionT {
        service(req).value.handleErrorWith { exception => handler(exception).map(Option(_)) }
      }
    }
}

trait HttpErrorHandler[E <: Throwable] extends RollbarNotifier {
  def handle(service: HttpService[IO]): HttpService[IO]
}

object HttpErrorHandler {
  def apply[E <: Throwable](implicit ev: HttpErrorHandler[E]) = ev
}

//   def handleErrors(
//       data: Either[Throwable, IO[Response[IO]]]): IO[Response[IO]] = {
//     data match {
//       case Right(response) => response
//       case Left(UnexpectedEnd) =>
//         Forbidden(
//           "Tiles cannot be produced or user is not authorized to view these tiles")
//       case Left(t: AmazonS3Exception) if t.getStatusCode == 404 =>
//         Gone("Underlying data for tile request are no longer available")
//       case Left(t: AmazonS3Exception) if t.getStatusCode == 403 =>
//         Forbidden(
//           "The Raster Foundry application is not authorized to access underlying data for this tile")
//       case Left(t: BadJWTException) =>
//         Forbidden(
//           "Token could not be verified. Please check authentication information and try again")
//       case Left(t) =>
//         sendError(t)
//         InternalServerError("Something went wrong")
//     }
//   }
// }
