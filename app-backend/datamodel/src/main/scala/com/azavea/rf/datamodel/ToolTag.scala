package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import io.circe.generic.JsonCodec

/** A user generate tag to track tools in the Raster Foundry lab
  *
  * @param id UUID Unique identifier for Tool Tag
  * @param createdAt Timestamp Creation time for tag
  * @param modifiedAt Timestamp Modification time for tag
  * @param createdBy String User ID that owns/created tag
  * @param modifiedBy String User ID that last modified tag
  * @param tag String Tag that is displayed to user
  */
@JsonCodec
final case class ToolTag(id: UUID,
                         createdAt: Timestamp,
                         modifiedAt: Timestamp,
                         createdBy: String,
                         modifiedBy: String,
                         owner: String,
                         tag: String)

object ToolTag {
  def create = Create.apply _

  def tupled = (ToolTag.apply _).tupled

  /** Case class to handle creating a new tool tag
    *
    * @param tag String user supplied string to use for tag
    */
  @JsonCodec
  final case class Create(tag: String, owner: Option[String])
      extends OwnerCheck {

    def toToolTag(user: User): ToolTag = {
      val now = new Timestamp(new java.util.Date().getTime)

      val ownerId = checkOwner(user, this.owner)

      ToolTag(UUID.randomUUID, now, now, user.id, user.id, ownerId, tag)
    }
  }
}
