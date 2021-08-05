package com.rasterfoundry.api.utils

import com.rasterfoundry.api.user.{Auth0Service, PasswordResetTicket}
import com.rasterfoundry.database.UserIntercomConversationDao
import com.rasterfoundry.database.notification.Notify
import com.rasterfoundry.datamodel._
import com.rasterfoundry.notification.email.Model.{HtmlBody, PlainBody}
import com.rasterfoundry.notification.intercom.Model.Message
import com.rasterfoundry.notification.intercom.{
  GroundworkConfig,
  IntercomConversation,
  LiveIntercomNotifier
}

import cats.effect.{ContextShift, IO}
import doobie.Transactor
import doobie.implicits._
import sttp.client3.SttpBackend

import scala.concurrent.Future

import java.{util => ju}

class IntercomNotifications(
    backend: SttpBackend[
      IO,
      Any
    ],
    xa: Transactor[IO]
)(implicit contextShift: ContextShift[IO])
    extends Config {

  private val intercomNotifier = new LiveIntercomNotifier[IO](backend)

  private val groundworkConfig =
    GroundworkConfig(intercomToken, intercomAdminId)

  private def getConversation(
      id: String
  ): IO[Option[UserIntercomConversation]] =
    UserIntercomConversationDao.getByUserId(id).transact(xa)

  private def insertConversation(
      userId: String,
      conversationId: String
  ): IO[UserIntercomConversation] =
    UserIntercomConversationDao
      .insertUserConversation(userId, conversationId)
      .transact(xa)

  def getDefaultShare(
      user: User,
      actionTypeOpt: Option[ActionType] = None
  ): List[ObjectAccessControlRule] = {
    val default = List(
      ObjectAccessControlRule(
        SubjectType.User,
        Some(user.id),
        ActionType.View
      ),
      ObjectAccessControlRule(
        SubjectType.User,
        Some(user.id),
        ActionType.Export
      )
    )
    val annotate = ObjectAccessControlRule(
      SubjectType.User,
      Some(user.id),
      ActionType.Annotate
    )
    val validate = ObjectAccessControlRule(
      SubjectType.User,
      Some(user.id),
      ActionType.Validate
    )
    actionTypeOpt match {
      case Some(ActionType.Validate) =>
        default :+ annotate :+ validate
      case Some(ActionType.Annotate) | None =>
        default :+ annotate
      case _ =>
        default
    }
  }

  def getSharer(sharingUser: User): String =
    if (sharingUser.email != "") {
      sharingUser.email
    } else if (sharingUser.personalInfo.email != "") {
      sharingUser.personalInfo.email
    } else {
      sharingUser.name
    }

  def shareNotify[T <: { val id: ju.UUID }](
      sharedUser: User,
      sharingUser: User,
      value: T,
      valueType: String
  ): IO[Either[Throwable, Unit]] = {
    val singularized = if (valueType.lastOption == Some('s')) {
      valueType.dropRight(1)
    } else {
      valueType
    }
    val message = Message(s"""
        | ${getSharer(sharingUser)} has shared a ${singularized} with you!
        | ${groundworkUrlBase}/app/${valueType}/${value.id}/overview
        | """.trim.stripMargin)
    IntercomConversation
      .notifyIO(
        sharedUser.id,
        message,
        groundworkConfig,
        intercomNotifier,
        getConversation,
        insertConversation
      )
      .attempt
  }

  def shareNotifyNewUser[T <: { val name: String }](
      bearerToken: ManagementBearerToken,
      sharingUser: User,
      newUserEmail: String,
      newUserId: String,
      sharingUserPlatform: Platform,
      value: T,
      valueType: String,
      getMessages: (String, T, PasswordResetTicket) => (HtmlBody, PlainBody)
  ): Future[Unit] = {
    val subject =
      s"""You've been invited to join the "${value.name}" $valueType on GroundWork!"""
    (for {
      ticket <- IO.fromFuture {
        IO {
          Auth0Service.createPasswordChangeTicket(
            bearerToken,
            s"$groundworkUrlBase/app/login",
            newUserId
          )
        }
      }
      (messageRich, messagePlain) = getMessages(
        getSharer(sharingUser),
        value,
        ticket
      )
      _ <-
        Notify
          .sendEmail(
            sharingUserPlatform.publicSettings,
            sharingUserPlatform.privateSettings,
            newUserEmail,
            subject,
            messageRich.underlying,
            messagePlain.underlying
          )
    } yield ()).attempt.void.unsafeToFuture
  }

}
