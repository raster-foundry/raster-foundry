package com.azavea.rf.datamodel

import spray.json.DefaultJsonProtocol._
import java.util.UUID
import java.sql.Timestamp

/** A user generate category to track model's in model lab
  *
  * @param id UUID Unique identifier for Model Category
  * @param createdAt Timestamp Creation time for category
  * @param modifiedAt Timestamp Modification time for category
  * @param createdBy String User ID that owns/created category
  * @param modifiedBy String User ID that last modified category
  * @param category String Category that is displayed to user
  */
case class ModelCategory(
    id: UUID,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    createdBy: String,
    modifiedBy: String,
    category: String
)

object ModelCategory {

  def create = Create.apply _

  def tupled = (ModelCategory.apply _).tupled

  implicit val defaultModelCategoryFormat = jsonFormat6(ModelCategory.apply _)

  case class Create(
      category: String
  ) {

    def toModelCategory(userId: String): ModelCategory = {
      val now = new Timestamp((new java.util.Date()).getTime())
      ModelCategory(
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
    implicit val defaultModelCategoryCreateFormat = jsonFormat1(Create.apply _)
  }
}
