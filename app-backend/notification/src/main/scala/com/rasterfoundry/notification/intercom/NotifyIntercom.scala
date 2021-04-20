package com.rasterfoundry.notification.intercom

import com.rasterfoundry.notification.intercom.Model._

import cats.effect.Sync
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import sttp.client._
import sttp.client.circe._
import sttp.model.Uri

trait IntercomNotifier[F[_]] {
  def createConversation(
      intercomToken: IntercomToken,
      userId: ExternalId,
      message: Message
  ): F[Conversation]

  def replyConversation(
      intercomToken: IntercomToken,
      adminId: UserId,
      conversationId: String,
      message: Message
  ): F[Unit]
}

class LiveIntercomNotifier[F[_]: Sync](
    backend: SttpBackend[
      F,
      Nothing,
      sttp.client.asynchttpclient.WebSocketHandler
    ]
) extends IntercomNotifier[F] {
  val sttpApiBase = "https://api.intercom.io"

  implicit val unsafeLoggerF = Slf4jLogger.getLogger[F]
  implicit val sttpBackend = backend

  def createConversation(
      intercomToken: IntercomToken,
      userId: ExternalId,
      message: Message
  ): F[Conversation] = {
    val uri = Uri(java.net.URI.create(s"$sttpApiBase/conversations"))
    Logger[F].debug(s"Creating a conversation with $userId at $uri") *>
      basicRequest.auth
        .bearer(intercomToken.underlying)
        .header("Accept", "application/json")
        .post(uri)
        .body(ConversationCreate(userId, message))
        .response(asJson[Conversation])
        .send() flatMap { res =>
      res.body match {
        case Left(err) =>
          throw new RuntimeException(
            s"Failed to create a conversation with ${userId}, error message: ${err.toString()}"
          )
        case Right(conversation) => conversation.pure[F]
      }
    }
  }

  def replyConversation(
      intercomToken: IntercomToken,
      adminId: UserId,
      conversationId: String,
      message: Message
  ): F[Unit] = {
    val uri = Uri(
      java.net.URI.create(s"$sttpApiBase/conversations/$conversationId/reply")
    )
    (Logger[F].debug(s"Replying a conversation ID: $conversationId at $uri") *>
      basicRequest.auth
        .bearer(intercomToken.underlying)
        .header("Accept", "application/json")
        .post(uri)
        .body(ConversationReply(adminId, message))
        .send() flatMap { resp =>
      resp.body match {
        case Left(err) => Logger[F].error(err)
        case _         => ().pure[F]
      }
    }).void
  }
}
