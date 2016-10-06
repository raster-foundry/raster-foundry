package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

case class Thumbnail(
  id: UUID,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  organizationId: UUID,
  widthPx: Int,
  heightPx: Int,
  sceneId: UUID,
  url: String,
  thumbnailSize: ThumbnailSize
)

case class CreateThumbnail(
  organizationId: UUID,
  widthPx: Int,
  heightPx: Int,
  thumbnailSize: ThumbnailSize,
  sceneId: UUID,
  url: String
) {
  def toThumbnail: Thumbnail = {
    val now = new Timestamp((new java.util.Date).getTime)
    Thumbnail(
      UUID.randomUUID, // primary key
      now, // created at,
      now, // modified at,
      organizationId,
      widthPx, // width in pixels
      heightPx, // height in pixels
      sceneId,
      url,
      thumbnailSize
    )
  }
}