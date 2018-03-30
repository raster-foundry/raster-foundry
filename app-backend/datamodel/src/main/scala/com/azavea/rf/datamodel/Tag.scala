package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

import io.circe._
import io.circe.generic.JsonCodec

/** A user generate tag to track analyses and tools in the Raster Foundry lab
  *
  * @param id UUID Unique identifier for Tool Tag
  * @param createdAt Timestamp Creation time for tag
  * @param modifiedAt Timestamp Modification time for tag
  * @param organizationId Timestamp Organization that owns tag
  * @param createdBy String User ID that owns/created tag
  * @param modifiedBy String User ID that last modified tag
  * @param tag String Tag that is displayed to user
  */

@JsonCodec
case class Tag(
  id: UUID,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  createdBy: String,
  modifiedBy: String,
  organizationId: UUID,
  tag: String,
  owner: String
)

object Tag {
  /** Case class to handle creating a new tool tag
    *
    * @param organizationId UUID organization to create tag for
    * @param tag String user supplied string to use for tag
    */
  @JsonCodec
  case class Create(
    organizationId: UUID,
    tag: String,
    owner: Option[String]
  ) extends OwnerCheck {

    def toAnalysisTag(user: User): Tag = {
      val now = new Timestamp((new java.util.Date()).getTime())

      val ownerId = checkOwner(user, this.owner)

      Tag(
        UUID.randomUUID,
        now,
        now,
        user.id,
        user.id,
        organizationId,
        tag,
        ownerId
      )
    }
  }
}
