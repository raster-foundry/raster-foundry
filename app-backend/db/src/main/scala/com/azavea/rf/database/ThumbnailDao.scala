package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.{Thumbnail, User}

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

import java.util.UUID


object ThumbnailDao extends Dao[Thumbnail] {

  val tableName = "thumbnails"

  val selectF = sql"""
    SELECT
      id, created_at, modified_at, organization_id, width_px, height_px,
      scene, url, thumbnail_size
    FROM
  """ ++ tableF

  def insert(thumbnail: Thumbnail): ConnectionIO[Thumbnail] = {
    (fr"""
      INSERT INTO thumbnails (
        id, created_at, modified_at, organization_id, width_px,
        height_px, scene, url, thumbnail_size
      ) VALUES (
        ${thumbnail.id}, NOW(), NOW(), ${thumbnail.organizationId}, ${thumbnail.widthPx},
        ${thumbnail.heightPx}, ${thumbnail.sceneId}, ${thumbnail.url}, ${thumbnail.thumbnailSize}
      )
    """).update.withUniqueGeneratedKeys[Thumbnail](
      "id", "created_at", "modified_at", "organization_id", "width_px",
      "height_px", "scene", "url", "thumbnail_size"
    )
  }

  def update(thumbnail: Thumbnail, thumbnailId: UUID): ConnectionIO[Int] = {
    (fr"""
      UPDATE thumbnails SET
        modified_at = NOW(),
        width_px = ${thumbnail.widthPx},
        height_px = ${thumbnail.heightPx},
        scene = ${thumbnail.sceneId},
        url = ${thumbnail.url},
        thumbnail_size = ${thumbnail.thumbnailSize}
      WHERE id = ${thumbnailId}
    """).update.run
  }

}
