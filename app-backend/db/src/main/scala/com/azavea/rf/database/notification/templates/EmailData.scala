package com.azavea.rf.database.notification.templates

final case class EmailData(
    subject: String,
    plainBody: String,
    richBody: String
)
