package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel.Thumbnail

import cats.implicits._
import doobie._, doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

object ThumbnailDao extends Dao[Thumbnail] {

  val tableName = "thumbnails"

  val selectF = sql"""
    SELECT
      id, created_at, modified_at, width_px, height_px,
      scene, url, thumbnail_size
    FROM
  """ ++ tableF

  def unsafeGetThumbnailById(thumbnailId: UUID): ConnectionIO[Thumbnail] =
    query.filter(thumbnailId).select

  def getThumbnailById(thumbnailId: UUID): ConnectionIO[Option[Thumbnail]] =
    query.filter(thumbnailId).selectOption

  def insertMany(thumbnails: List[Thumbnail]): ConnectionIO[Int] = {
    val insertSql = """
      INSERT INTO thumbnails (
        id, created_at, modified_at, width_px,
        height_px, scene, url, thumbnail_size
      ) VALUES (
        ?, ?, ?, ?, ?, ?, ?, ?
      )
    """
    Update[Thumbnail](insertSql).updateMany(thumbnails.toList)
  }

  def insert(thumbnail: Thumbnail): ConnectionIO[Thumbnail] = {
    fr"""
      INSERT INTO thumbnails (
        id, created_at, modified_at, width_px,
        height_px, scene, url, thumbnail_size
      ) VALUES (
        ${thumbnail.id}, NOW(), NOW(), ${thumbnail.widthPx},
        ${thumbnail.heightPx}, ${thumbnail.sceneId}, ${thumbnail.url}, ${thumbnail.thumbnailSize}
      )
    """.update.withUniqueGeneratedKeys[Thumbnail](
      "id",
      "created_at",
      "modified_at",
      "width_px",
      "height_px",
      "scene",
      "url",
      "thumbnail_size"
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
