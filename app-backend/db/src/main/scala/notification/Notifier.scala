package com.rasterfoundry.database.notification

import com.rasterfoundry.database.notification.templates.EmailData
import com.rasterfoundry.datamodel.User

import doobie.ConnectionIO

trait Notifier {
  def builder(messageType: MessageType): ConnectionIO[EmailData]
  def userFinder(messageType: MessageType): ConnectionIO[List[User]]
  def send: ConnectionIO[Either[Throwable, Unit]]
}
