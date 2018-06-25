package com.azavea.rf.database.notification

sealed abstract class MessageType(val repr: String) {
  override def toString = repr
}

object MessageType {
  case object GroupRequest extends MessageType("GROUPREQUEST")
  case object GroupInvitation extends MessageType("GROUPINVITATION")
}
