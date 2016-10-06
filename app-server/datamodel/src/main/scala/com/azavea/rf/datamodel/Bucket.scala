package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

case class Bucket(
  id: UUID,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  organizationId: UUID,
  createdBy: String,
  modifiedBy: String,
  name: String,
  slugLabel: String,
  description: String,
  visibility: Visibility,
  tags: Option[List[String]] = None // TODO: List.empty == None, this should be a naked List
)

/** Case class for bucket creation */
case class CreateBucket(
  organizationId: UUID,
  name: String,
  description: String,
  visibility: Visibility,
  tags: List[String]
) {
  def toBucket(userId: String): Bucket = {
    val now = new Timestamp((new java.util.Date()).getTime())
    Bucket(
      UUID.randomUUID, // primary key
      now, // createdAt
      now, // modifiedAt
      organizationId,
      userId, // createdBy
      userId, // modifiedBy
      name,
      slugify(name),
      description,
      visibility,
      Some(tags)
    )
  }

  def slugify(input: String): String = {
    import java.text.Normalizer
    Normalizer.normalize(input, Normalizer.Form.NFD)
      .replaceAll("[^\\w\\s-]", ""
        .replace('-', ' ')
        .trim
        .replaceAll("\\s+", "-")
        .toLowerCase)
  }
}
