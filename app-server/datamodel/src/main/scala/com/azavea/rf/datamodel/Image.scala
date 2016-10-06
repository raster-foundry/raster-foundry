package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

case class Image(
  id: UUID,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  organizationId: UUID,
  createdBy: String,
  modifiedBy: String,
  rawDataBytes: Int,
  visibility: Visibility,
  filename: String,
  sourceUri: String,
  scene: UUID,
  bands: List[String],
  imageMetadata: Map[String, Any]
)

case class CreateImage(
  organizationId: UUID,
  rawDataBytes: Int,
  visibility: Visibility,
  filename: String,
  sourceUri: String,
  scene: UUID,
  bands: List[String],
  imageMetadata: Map[String, Any]
) {
  def toImage(userId: String): Image = {
    val now = new Timestamp((new java.util.Date).getTime)

    Image(
      UUID.randomUUID, // primary key
      now, // createdAt
      now, // modifiedAt
      organizationId,
      userId, // createdBy: String,
      userId, // modifiedBy: String,
      rawDataBytes,
      visibility,
      filename,
      sourceUri,
      scene,
      bands,
      imageMetadata
    )
  }
}
