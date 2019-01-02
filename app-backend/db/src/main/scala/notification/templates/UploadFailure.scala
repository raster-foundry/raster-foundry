package com.rasterfoundry.database.notification.templates

import java.util.UUID

import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._
import doobie.ConnectionIO

final case class UploadFailure(uploadId: UUID, platformId: UUID) {
  def build: ConnectionIO[EmailData] = {
    for {
      platform <- PlatformDao.unsafeGetPlatformById(platformId)
      platformHost = platform.publicSettings.platformHost
        .getOrElse("app.rasterfoundry.com")
      upload <- UploadDao.unsafeGetUploadById(uploadId)
      owner <- UserDao.unsafeGetUserById(upload.owner)
    } yield {
      val plainFiles = upload.files.mkString(", ")
      val richFiles = upload.files map { fname =>
        s"<li>$fname</li>"
      }
      val plainBody = s"""
      | Your upload ${upload.id} failed to process successfully. It had the following files:
      |
      | ${plainFiles}
      |
      | Support is available via in-app chat at ${platformHost} or less quickly
      | via email to ${platform.publicSettings.emailSupport}.
      |
      | -- The ${platform.name} Team
      """.trim.stripMargin
      val richBody = s"""
<html>
  <p> Your upload ${upload.id} failed to process successfully. It had the following files: ${richFiles} </p>
  <p>
    Support is available via in-app chat at ${platformHost} or less quickly via email to ${platform.publicSettings.emailSupport}.
  </p>
  <p> The ${platform.name} Team </p>
</html>
      """
      val subject = s"Upload ${upload.id} failed to process successfully"

      EmailData(subject, plainBody, richBody)
    }
  }
}
