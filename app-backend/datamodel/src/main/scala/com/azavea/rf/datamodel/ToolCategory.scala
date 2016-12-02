package com.azavea.rf.datamodel

import spray.json.DefaultJsonProtocol._
import java.util.UUID
import java.sql.Timestamp

/** A user generate category to track tools in model lab
  *
  * @param id UUID Unique identifier for Tool Category
  * @param createdAt Timestamp Creation time for category
  * @param modifiedAt Timestamp Modification time for category
  * @param createdBy String User ID that owns/created category
  * @param modifiedBy String User ID that last modified category
  * @param category String Category that is displayed to user
  */
case class ToolCategory(
    id: UUID,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    createdBy: String,
    modifiedBy: String,
    category: String
)

object ToolCategory {

  def create = Create.apply _

  def tupled = (ToolCategory.apply _).tupled

  implicit val defaultToolCategoryFormat = jsonFormat6(ToolCategory.apply _)

  case class Create(
      category: String
  ) {

    def toToolCategory(userId: String): ToolCategory = {
      val now = new Timestamp((new java.util.Date()).getTime())
      ToolCategory(
        UUID.randomUUID,
        now,
        now,
        userId,
        userId,
        category
      )
    }
  }

  object Create {
    implicit val defaultToolCategoryCreateFormat = jsonFormat1(Create.apply _)
  }
}
