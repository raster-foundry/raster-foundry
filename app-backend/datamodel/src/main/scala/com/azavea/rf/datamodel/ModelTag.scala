package com.azavea.rf.datamodel

import spray.json.DefaultJsonProtocol._
import java.util.UUID
import java.sql.Timestamp

/** A user generate tag to track model's in model lab
  *
  * @param id UUID Unique identifier for Model Tag
  * @param createdAt Timestamp Creation time for tag
  * @param modifiedAt Timestamp Modification time for tag
  * @param organizationId Timestamp Organization that owns tag
  * @param createdBy String User ID that owns/created tag
  * @param modifiedBy String User ID that last modified tag
  * @param tag String Tag that is displayed to user
  */
case class ModelTag(
    id: UUID,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    organizationId: UUID,
    createdBy: String,
    modifiedBy: String,
    tag: String
)

object ModelTag {

  def create = Create.apply _

  def tupled = (ModelTag.apply _).tupled

  implicit val defaultModelTagFormat = jsonFormat7(ModelTag.apply _)

  /** Case class to handle creating a new model tag
    *
    * @param organizationId UUID organization to create tag for
    * @param tag String user supplied string to use for tag
    */
  case class Create(
      organizationId: UUID,
      tag: String
  ) {

    def toModelTag(userId: String): ModelTag = {
      val now = new Timestamp((new java.util.Date()).getTime())
      ModelTag(
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
    implicit val defaultModelTagCreateFormat = jsonFormat2(Create.apply _)
  }
}
