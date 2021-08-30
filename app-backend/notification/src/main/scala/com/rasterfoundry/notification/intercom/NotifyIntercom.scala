package com.rasterfoundry.notification.intercom

import com.rasterfoundry.notification.intercom.Model._

import cats.effect.Sync
import cats.effect.{Blocker, ConcurrentEffect, Resource}
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.Method._
import org.http4s.Response
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.client._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.{Header, Uri}

import java.util.concurrent.Executors

trait IntercomNotifier[F[_]] {
  def createConversation(
      intercomToken: IntercomToken,
      userId: ExternalId,
      message: Message
  ): F[Conversation]

  def replyConversation(
      intercomToken: IntercomToken,
      adminId: AdminId,
      conversationId: String,
      message: Message
  ): F[Unit]
}

class LiveIntercomNotifier[F[_]: Sync: ConcurrentEffect]
    extends IntercomNotifier[F]
    with Http4sClientDsl[F] {

  val blockingPool = Executors.newCachedThreadPool()
  val blocker = Blocker.liftExecutorService(blockingPool)
  val httpClient: Resource[F, Client[F]] =
    BlazeClientBuilder[F](blocker.blockingContext).resource

  val apiBase = "https://api.intercom.io"

  implicit val unsafeLoggerF = Slf4jLogger.getLogger[F]

  def createConversation(
      intercomToken: IntercomToken,
      userId: ExternalId,
      message: Message
  ): F[Conversation] =
    httpClient.use { client =>
      val uri = Uri.unsafeFromString(s"$apiBase/conversations")
      Logger[F].debug(s"Creating a conversation with $userId at $uri") *> {
        val authHeader =
          Header("Authorization", s"Bearer ${intercomToken.underlying}")
        val acceptHeader = Header("Accept", "application/json")
        val request = POST(
          ConversationCreate(userId, message),
          uri,
          authHeader,
          acceptHeader
        )
        client.expect[Conversation](request)
      }
    }

  def replyConversation(
      intercomToken: IntercomToken,
      adminId: AdminId,
      conversationId: String,
      message: Message
  ): F[Unit] =
    httpClient.use { client =>
      val uri =
        Uri.unsafeFromString(s"$apiBase/conversations/$conversationId/reply")
      Logger[F].debug(
        s"Replying a conversation ID: $conversationId at $uri"
      ) *> {
        val authHeader =
          Header("Authorization", s"Bearer ${intercomToken.underlying}")
        val acceptHeader = Header("Accept", "application/json")

        val request =
          POST(
            ConversationReply(adminId, message),
            uri,
            authHeader,
            acceptHeader
          )
        request flatMap { req =>
          client.run(req) use {
            case Response(status, _, _, _, _) if status.code >= 400 =>
              Logger[F].error(
                s"Could not reply to conversation $conversationId at $uri"
              )
            case _ =>
              Logger[F].info(
                s"Notified user on conversation id $conversationId")
          }

        }
      }
    }
}
