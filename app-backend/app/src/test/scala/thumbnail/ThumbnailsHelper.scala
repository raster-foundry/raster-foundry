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

  val landsatId = UUID.fromString("697a0b91-b7a8-446e-842c-97cda155554d")

  val newScene = Scene.Create(
    None, publicOrgId, 0, Visibility.Public, List("Test", "Public", "Low Resolution"), landsatId,
    Map("instrument type" -> "satellite", "splines reticulated" -> 0):Map[String, Any], None,
    Some(Timestamp.from(Instant.parse("2016-09-19T14:41:58.408544Z"))),
    JobStatus.Processing, JobStatus.Processing, None, None, "test scene project",
    None, None, List.empty[String], List.empty[Image.Banded], List.empty[Thumbnail.Identified], None
  )

  def newThumbnail(size: ThumbnailSize, sceneId: UUID): Thumbnail.Create = {
    Thumbnail.create(publicOrgId, size, 128, 128, sceneId, "https://website.com/example.png")
  }
}
