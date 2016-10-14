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

  val newScene = CreateScene(
    publicOrgId, 0, Visibility.PUBLIC, 20.2f, List("Test", "Public", "Low Resolution"), "TEST_ORG",
    Map("instrument type" -> "satellite", "splines reticulated" -> 0):Map[String, Any], None,
    Some(Timestamp.from(Instant.parse("2016-09-19T14:41:58.408544Z"))),
    JobStatus.PROCESSING, JobStatus.PROCESSING, JobStatus.PROCESSING, None, None, "test scene bucket",
    List(): List[SceneImage], None, List(): List[SceneThumbnail]
  )

  def newThumbnail(size: ThumbnailSize, sceneId: UUID): CreateThumbnail = {
    CreateThumbnail(publicOrgId, 128, 128, size, sceneId, "https://website.com/example.png")
  }
}
