package com.azavea.rf.database.notification

import com.azavea.rf.database.notification.templates.EmailData
import com.azavea.rf.datamodel.User

import doobie.ConnectionIO

trait Notifier {
  def builder(messageType: MessageType): ConnectionIO[EmailData]
  def userFinder(messageType: MessageType): ConnectionIO[List[User]]
  def send: ConnectionIO[Either[Throwable, Unit]]
}
