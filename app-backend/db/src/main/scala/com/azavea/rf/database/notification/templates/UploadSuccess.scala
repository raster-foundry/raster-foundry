package com.azavea.rf.database.notification.templates

import com.azavea.rf.database._
import com.azavea.rf.datamodel._

import cats.implicits._
import doobie.ConnectionIO
import doobie.implicits._

import java.util.UUID

case class UploadSuccess(
  uploadId: UUID,
  platformId: UUID
) {
  def build: ConnectionIO[EmailData] = {
    for {
      platform <- PlatformDao.unsafeGetPlatformById(platformId)
      platformHost = platform.publicSettings.platformHost.getOrElse("app.rasterfoundry.com")
      upload <- UploadDao.unsafeGetUploadById(uploadId)
      owner <- UserDao.unsafeGetUserById(upload.owner)
      uploadProject <- upload.projectId match {
        case Some(id) => ProjectDao.getProjectById(id, Some(owner))
        case _ => None.pure[ConnectionIO]
      }
    } yield {
      val plainUploadLocation = uploadProject.map(project =>
        s"| View this upload in your project ${project.name} at https://${platformHost}/projects/${project.id}"
      ).getOrElse(
        s"| View your uploads at https://${platformHost}/imports/rasters"
      )

      val richUploadLocation = uploadProject.map(project =>
        s"""
<p>View this upload in your project
  <a href="https://${platformHost}/projects/${project.id}">${project.name}</a>
</p>
      """
      ).getOrElse(
        s"""
<p>You can view your uploads
  <a href="https://${platformHost}/imports/rasters>here</a>.
</p>
      """
      )
      val plainBody = s"""
      | Your upload ${upload.id} has been processed successfully.
      |
      ${plainUploadLocation}
      |
      | -- The ${platform.name} Team
      """.trim.stripMargin
      val richBody = s"""
<html>
  <p>Your upload ${upload.id} has been processed successfully.</p>
  ${richUploadLocation}
  <p>
    -- The ${platform.name} Team
  </p>
</html>
      """.trim.stripMargin
      val subject = s"Upload ${upload.id} completed successfully"

      EmailData(subject, plainBody, richBody)
    }
  }
}
