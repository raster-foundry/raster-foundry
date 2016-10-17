package com.azavea.rf.thumbnail

import java.util.UUID
import com.azavea.rf.datamodel._
import com.azavea.rf.AuthUtils
import java.sql.Timestamp
import java.time.Instant


trait ThumbnailSpecHelper {
  val authorization = AuthUtils.generateAuthHeader("Default")
  val baseThumbnail = "/api/thumbnails/"
  val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")
  val fakeOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7725")

  val newScene = Scene.Create(
    publicOrgId, 0, Visibility.Public, 20.2f, List("Test", "Public", "Low Resolution"), "TEST_ORG",
    Map("instrument type" -> "satellite", "splines reticulated" -> 0):Map[String, Any], None,
    Some(Timestamp.from(Instant.parse("2016-09-19T14:41:58.408544Z"))),
    JobStatus.Processing, JobStatus.Processing, JobStatus.Processing, None, None, "test scene bucket",
    List.empty[Image.Identified], None, List.empty[Thumbnail.Identified]
  )

  def newThumbnail(size: ThumbnailSize, sceneId: UUID): Thumbnail.Create = {
    Thumbnail.create(publicOrgId, size, 128, 128, sceneId, "https://website.com/example.png")
  }
}
