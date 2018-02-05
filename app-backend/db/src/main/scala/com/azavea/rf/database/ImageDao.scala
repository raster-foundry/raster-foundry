package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import io.circe._

import java.sql.Timestamp
import java.util.{Date, UUID}


object ImageDao extends Dao[Image] {

  val tableName = "images"

  val selectF = sql"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, organization_id,
      owner, raw_data_bytes, visibility, filename, sourceuri, scene,
      image_metadata, resolution_meters, metadata_files
    FROM
  """ ++ tableF

  def select(id: UUID) =
    (selectF ++ fr"WHERE id = $id").query[Image].unique

  def create(
    user: User,
    organizationId: UUID,
    rawDataBytes: Long,
    visibility: Visibility,
    filename: String,
    sourceUri: String,
    scene: UUID,
    imageMetadata: Json,
    owner: Option[String],
    resolutionMeters: Float,
    metadataFiles: List[String]
  ): ConnectionIO[Image] = {
    val id = UUID.randomUUID
    val now = new Timestamp((new java.util.Date()).getTime())
    val ownerId = util.Ownership.checkOwner(user, owner)
    (sql"INSERT INTO" ++ tableF ++ fr"""
        (id, created_at, created_by, modified_at, modified_by, organization_id,
        owner, raw_data_bytes, visibility, filename, sourceuri, scene,
        image_metadata, resolution_meters, metadata_files)
      VALUES
        ($id, $now, ${user.id}, $now, ${user.id}, $organizationId,
        $ownerId, $rawDataBytes, $visibility, $filename, $sourceUri, $scene,
        $imageMetadata, $resolutionMeters, $metadataFiles)
    """).update.withUniqueGeneratedKeys[Image](
        "id", "created_at", "created_by", "modified_at", "modified_by", "organization_id",
        "owner", "raw_data_bytes", "visibility", "filename", "sourceuri", "scene",
        "image_metadata", "resolution_meters", "metadata_files"
    )
  }
}

