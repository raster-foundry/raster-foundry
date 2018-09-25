package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import io.circe.generic.JsonCodec

@JsonCodec
final case class Thumbnail(id: UUID,
                           createdAt: Timestamp,
                           modifiedAt: Timestamp,
                           widthPx: Int,
                           heightPx: Int,
                           sceneId: UUID,
                           url: String,
                           thumbnailSize: ThumbnailSize) {
  def toThumbnail: Thumbnail = this
}

object Thumbnail {
  def tupled = (Thumbnail.apply _).tupled

  def create = Create.apply _

  def identified = Identified.apply _

  /** Thumbnail class prior to ID assignment */
  @JsonCodec
  final case class Create(thumbnailSize: ThumbnailSize,
                          widthPx: Int,
                          heightPx: Int,
                          sceneId: UUID,
                          url: String) {
    def toThumbnail: Thumbnail = {
      val now = new Timestamp((new java.util.Date).getTime)
      Thumbnail(
        UUID.randomUUID, // primary key
        now, // created at,
        now, // modified at,
        widthPx, // width in pixels
        heightPx, // height in pixels
        sceneId,
        url,
        thumbnailSize
      )
    }
  }

  /** Thumbnail class when posted with an ID */
  @JsonCodec
  final case class Identified(id: Option[UUID],
                              thumbnailSize: ThumbnailSize,
                              widthPx: Int,
                              heightPx: Int,
                              sceneId: UUID,
                              url: String) {
    def toThumbnail: Thumbnail = {
      val now = new Timestamp(new java.util.Date().getTime)
      Thumbnail(
        this.id.getOrElse(UUID.randomUUID), // primary key
        now, // createdAt
        now, // modifiedAt
        this.widthPx,
        this.heightPx,
        this.sceneId,
        this.url,
        this.thumbnailSize
      )
    }
  }
}
