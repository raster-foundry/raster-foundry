package com.rasterfoundry.database.notification

import java.util.UUID

import cats.implicits._
import com.rasterfoundry.database._
import com.rasterfoundry.database.notification.templates._
import com.rasterfoundry.datamodel._
import doobie.ConnectionIO

final case class UploadNotifier(
    platformId: UUID,
    uploadId: UUID,
    messageType: MessageType
) extends Notifier {

  def builder(messageType: MessageType): ConnectionIO[EmailData] = {
    messageType match {
      case MessageType.UploadSucceeded =>
        UploadSuccess(uploadId, platformId).build
      case MessageType.UploadFailed => UploadFailure(uploadId, platformId).build
      case _ =>
        throw new Exception(
          s"Attempted to send upload status message with invalid message type $messageType")
    }
  }

  def userFinder(messageType: MessageType): ConnectionIO[List[User]] =
    for {
      upload <- UploadDao.unsafeGetUploadById(uploadId)
      owner <- UserDao.unsafeGetUserById(upload.owner)
    } yield { List(owner) }

  def send: ConnectionIO[Either[Throwable, Unit]] =
    Notify
      .sendNotification(platformId, messageType, builder, userFinder)
      .attempt
}
