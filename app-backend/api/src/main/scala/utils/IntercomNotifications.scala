package com.rasterfoundry.api.utils

import com.rasterfoundry.datamodel._

import cats.effect.{Async, ContextShift, IO}
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
import com.rasterfoundry.api.user.{Auth0Service, PasswordResetTicket}
import com.rasterfoundry.notification.email.Model.{HtmlBody, PlainBody}

import scala.concurrent.Future
import java.{util => ju}
import com.rasterfoundry.notification.intercom.LiveIntercomNotifier
import com.rasterfoundry.notification.intercom.Model.{ExternalId, Message}
import com.rasterfoundry.database.notification.Notify

trait IntercomNotifications extends Config {
  implicit val contextShift: ContextShift[IO]

  private val intercomNotifierIO = for {
    backend <- getBackend
    notifier = new LiveIntercomNotifier[IO](backend)
  } yield notifier

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

  val getBackend = for {
    backendRef <- Async.memoize {
      AsyncHttpClientCatsBackend[IO]()
    }
    backend <- backendRef
  } yield backend

  def shareNotify[T <: { val id: ju.UUID }](
      sharedUser: User,
      sharingUser: User,
      value: T,
      valueType: String
  ): IO[Either[Throwable, Unit]] =
    intercomNotifierIO flatMap { intercomNotifier =>
      intercomNotifier
        .notifyUser(
          intercomToken,
          intercomAdminId,
          ExternalId(sharedUser.id),
          Message(s"""
        | ${getSharer(sharingUser)} has shared a $valueType with you!
        | ${groundworkUrlBase}/app/${valueType}s/${value.id}/overview
        | """.trim.stripMargin)
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
      _ <- Notify
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
