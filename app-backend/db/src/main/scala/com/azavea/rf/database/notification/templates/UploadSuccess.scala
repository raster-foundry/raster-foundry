package com.azavea.rf.database.notification.templates

import java.util.UUID

import cats.implicits._
import com.azavea.rf.database._
import com.azavea.rf.datamodel._
import doobie.ConnectionIO
import doobie.implicits._

final case class UploadSuccess(uploadId: UUID, platformId: UUID) {
  def build: ConnectionIO[EmailData] = {
    for {
      platform <- PlatformDao.unsafeGetPlatformById(platformId)
      platformHost = platform.publicSettings.platformHost
        .getOrElse("app.rasterfoundry.com")
      upload <- UploadDao.unsafeGetUploadById(uploadId)
      owner <- UserDao.unsafeGetUserById(upload.owner)
      uploadProject <- upload.projectId match {
        case Some(id) => ProjectDao.getProjectById(id)
        case _        => None.pure[ConnectionIO]
      }
    } yield {
      val signature = s"- The ${platform.name} Team"
      val importsUrl = s"https://${platformHost}/imports/rasters?page=1"
      val sceneAddedMsg = "A scene has successfully been added to your project"
      val statusMsg =
        "You can check the status of all of your processing scenes any time"
      val withImportIDMsg = s"with Import ID ${upload.id}"
      val newSceneMsg = "A new scene has been added to your project"

      uploadProject
        .map(project => {
          val projectUrl =
            s"https://${platformHost}/projects/edit/${project.id}"
          val subject = s"""${newSceneMsg} "${project.name}""""
          val plainBody = s"""
          | ${sceneAddedMsg} at ${projectUrl} ${withImportIDMsg}. ${statusMsg} at: ${importsUrl}.
          |
          | ${signature}
          """.trim.stripMargin
          val richBody = s"""
<html>
  <p>
    ${sceneAddedMsg} <a href="${projectUrl}">${project.name}</a> ${withImportIDMsg}. ${statusMsg} on the <a href="${importsUrl}">Imports Page</a>.
  </p>
  <p>
    ${signature}
  </p>
</html>""".trim.stripMargin
          (subject, plainBody, richBody)
        })
        .getOrElse({
          val subject = s"${newSceneMsg} ${withImportIDMsg}"
          val plainBody = s"""
          | ${sceneAddedMsg} ${withImportIDMsg}. ${statusMsg} at: ${importsUrl}
          |
          | ${signature}
          """.trim.stripMargin
          val richBody = s"""
<html>
  <p>
    ${sceneAddedMsg} ${withImportIDMsg}. ${statusMsg} on the <a href="${importsUrl}">Imports Page</a>.
  </p>
  <p>
    ${signature}
  </p>
</html>""".trim.stripMargin
          (subject, plainBody, richBody)
        }) match {
        case (subject, plainBody, richBody) =>
          EmailData(subject, plainBody, richBody)
      }
    }
  }
}
