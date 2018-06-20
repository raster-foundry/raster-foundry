package com.azavea.rf.database.notification.templates

case class EmailData(
  subject: String,
  plainBody: String,
  richBody: String
)
