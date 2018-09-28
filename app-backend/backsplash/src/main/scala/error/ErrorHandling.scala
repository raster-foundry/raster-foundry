package com.azavea.rf.backsplash.error

import com.azavea.rf.common.RollbarNotifier

import cats.data.NonEmptyList
import cats.effect._
import cats.implicits._
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.nimbusds.jwt.proc.BadJWTException
import doobie.util.invariant.UnexpectedEnd
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._

trait ErrorHandling extends RollbarNotifier {
  def handleErrors(
      data: Either[Throwable, IO[Response[IO]]]): IO[Response[IO]] = {
    data match {
      case Right(response) => response
      case Left(UnexpectedEnd) =>
        Forbidden(
          "Tiles cannot be produced or user is not authorized to view these tiles")
      case Left(t: AmazonS3Exception) if t.getStatusCode == 404 =>
        Gone("Underlying data for tile request are no longer available")
      case Left(t: AmazonS3Exception) if t.getStatusCode == 403 =>
        Forbidden(
          "The Raster Foundry application is not authorized to access underlying data for this tile")
      case Left(t: BadJWTException) =>
        Forbidden(
          "Token could not be verified. Please check authentication information and try again")
      case Left(t @ MetadataError(m)) =>
        sendError(t)
        InternalServerError(m)
      case Left(SingleBandOptionsError(m)) => BadRequest(m)
      case Left(UningestedScenes(m))       => NotFound(m)
      case Left(UnknownSceneType(m))       => BadRequest(m)
      case Left(NotAuthorized(_)) =>
        Forbidden(
          "Tiles cannot be produced or user is not authorized to view these tiles")
      case Left(t) =>
        sendError(t)
        InternalServerError("Something went wrong")
    }
  }
}
