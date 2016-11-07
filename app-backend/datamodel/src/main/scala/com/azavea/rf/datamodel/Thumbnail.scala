package com.azavea.rf.datamodel

import spray.json._
import spray.json.DefaultJsonProtocol._
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
) {
  def toThumbnail = this
}

object Thumbnail {

  def tupled = (Thumbnail.apply _).tupled

  def create = Create.apply _

  def identified = Identified.apply _

  implicit val defaultThumbnailFormat = jsonFormat9(Thumbnail.apply)

  /** Thumbnail class prior to ID assignment */
  case class Create(
    organizationId: UUID,
    thumbnailSize: ThumbnailSize,
    widthPx: Int,
    heightPx: Int,
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

  object Create {
    implicit val defaultCreateFormat = jsonFormat6(create)
  }

  /** Thumbnail class when posted with an ID */
  case class Identified(
    id: Option[UUID],
    organizationId: UUID,
    thumbnailSize: ThumbnailSize,
    widthPx: Int,
    heightPx: Int,
    sceneId: UUID,
    url: String
  ) {
    def toThumbnail(userId: String): Thumbnail = {
      val now = new Timestamp((new java.util.Date()).getTime())
      Thumbnail(
        this.id.getOrElse(UUID.randomUUID), // primary key
        now, // createdAt
        now, // modifiedAt
        this.organizationId,
        this.widthPx,
        this.heightPx,
        this.sceneId,
        this.url,
        this.thumbnailSize
      )
    }
  }

  object Identified {
    implicit val defaultIdentifiedFormat = jsonFormat7(Identified.apply)
  }
}
