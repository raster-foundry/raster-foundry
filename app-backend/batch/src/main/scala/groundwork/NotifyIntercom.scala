package com.rasterfoundry.batch.groundwork

import types._

import cats.effect.Async
import cats.implicits._
import com.softwaremill.sttp.circe._
import com.softwaremill.sttp._

trait IntercomNotifier[F[_]] {
  def notifyUser(
      intercomToken: IntercomToken,
      userId: UserId,
      msg: Message
  ): F[Unit]
  def userByExternalId(
      intercomToken: IntercomToken,
      externalId: ExternalId
  ): F[IntercomSearchResponse]
}

class LiveIntercomNotifier[F[_]: Async](
    implicit backend: SttpBackend[F, Nothing]
) extends IntercomNotifier[F] {
  val sttpApiBase = "https://api.intercom.io"

  def responseAsBody[T](
      resp: Either[String, Either[DeserializationError[io.circe.Error], T]],
      fallback: => T
  ): T =
    resp match {
      case Left(err) =>
        fallback
      case Right(deserialized) =>
        deserialized match {
          case Left(err) =>
            fallback
          case Right(body) =>
            body
        }
    }

  def notifyUser(
      intercomToken: IntercomToken,
      userId: UserId,
      msg: Message
  ): F[Unit] = ???

  def userByExternalId(
      intercomToken: IntercomToken,
      externalId: ExternalId
  ): F[IntercomSearchResponse] = {
    val uri = Uri(java.net.URI.create(s"$sttpApiBase/contacts/search"))
    val resp = sttp.auth
      .bearer(intercomToken.underlying)
      .post(uri)
      .body(IntercomPost(IntercomSearchQuery(externalId)))
      .response(asJson[IntercomSearchResponse])
      .send()
    resp map { r =>
      responseAsBody(r.body, IntercomSearchResponse.empty)
    }
  }
}
