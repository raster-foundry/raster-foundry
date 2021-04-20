package com.rasterfoundry.notification.intercom

import com.rasterfoundry.datamodel.{UserIntercomConversation}
import com.rasterfoundry.notification.intercom.Model._

import cats.effect.IO
import cats.syntax.all._

case class GroundworkConfig(
    intercomToken: IntercomToken,
    intercomAdminId: UserId
)

object IntercomConversation {
  def notifyIO(
      userId: String,
      message: Message,
      groundworkConfig: GroundworkConfig,
      notifier: IntercomNotifier[IO],
      getConversation: String => IO[Option[UserIntercomConversation]],
      insertConversation: (String, String) => IO[UserIntercomConversation]
  ): IO[Unit] =
    for {
      conversationOpt <- getConversation(userId)
      conversationIdOpt <- conversationOpt match {
        case Some(conversation) =>
          notifier.replyConversation(
            groundworkConfig.intercomToken,
            groundworkConfig.intercomAdminId,
            conversation.conversationId,
            message
          ) *> Option.empty.pure[IO]
        case _ =>
          notifier.createConversation(
            groundworkConfig.intercomToken,
            ExternalId(userId),
            message
          ) map { convo => Some(convo.conversationId.underlying) }
      }
      _ <- conversationIdOpt traverse { conversationId =>
        insertConversation(userId, conversationId)
      }
    } yield ()
}
