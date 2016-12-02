package com.azavea.rf.datamodel

import spray.json.DefaultJsonProtocol._
import java.util.UUID
import java.sql.Timestamp

/** A user generate tag to track tools in model lab
  *
  * @param id UUID Unique identifier for Tool Tag
  * @param createdAt Timestamp Creation time for tag
  * @param modifiedAt Timestamp Modification time for tag
  * @param organizationId Timestamp Organization that owns tag
  * @param createdBy String User ID that owns/created tag
  * @param modifiedBy String User ID that last modified tag
  * @param tag String Tag that is displayed to user
  */
case class ToolTag(
    id: UUID,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    organizationId: UUID,
    createdBy: String,
    modifiedBy: String,
    tag: String
)

object ToolTag {

  def create = Create.apply _

  def tupled = (ToolTag.apply _).tupled

  implicit val defaultToolTagFormat = jsonFormat7(ToolTag.apply _)

  /** Case class to handle creating a new tool tag
    *
    * @param organizationId UUID organization to create tag for
    * @param tag String user supplied string to use for tag
    */
  case class Create(
      organizationId: UUID,
      tag: String
  ) {

    def toToolTag(userId: String): ToolTag = {
      val now = new Timestamp((new java.util.Date()).getTime())
      ToolTag(
        UUID.randomUUID,
        now,
        now,
        organizationId,
        userId,
        userId,
        tag
      )
    }
  }

  object Create {
    implicit val defaultToolTagCreateFormat = jsonFormat2(Create.apply _)
  }
}
