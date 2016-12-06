package com.azavea.rf.datamodel

import spray.json._
import spray.json.DefaultJsonProtocol._
import java.util.UUID
import java.sql.Timestamp

case class Tool(
  id: UUID,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  createdBy: String,
  modifiedBy: String,
  organizationId: UUID,
  title: String,
  description: String,
  requirements: String,
  license: String,
  visibility: Visibility,
  compatibleDataSources: List[String] = List.empty,
  stars: Float = 0.0f
) {
  def withRelatedFromComponents(toolTagIds: Seq[UUID], toolCategoryIds: Seq[UUID]):
      Tool.WithRelated = Tool.WithRelated(
    this.id,
    this.createdAt,
    this.modifiedAt,
    this.createdBy,
    this.modifiedBy,
    this.organizationId,
    this.title,
    this.description,
    this.requirements,
    this.license,
    this.visibility,
    this.compatibleDataSources,
    this.stars,
    toolTagIds,
    toolCategoryIds
  )
}

/** Case class for tool creation */
object Tool {

  def create = Create.apply _

  def tupled = (Tool.apply _).tupled

  implicit val defaultToolFormat = jsonFormat13(Tool.apply _)

  case class Create(
    organizationId: UUID,
    title: String,
    description: String,
    requirements: String,
    license: String,
    visibility: Visibility,
    compatibleDataSources: List[String],
    stars: Float,
    tags: Seq[UUID],
    categories: Seq[UUID]
  ) {
    def toToolWithRelatedTuple(userId: String): (Tool, Seq[ToolTagToTool], Seq[ToolCategoryToTool]) = {
      val now = new Timestamp((new java.util.Date()).getTime())
      val toolId = UUID.randomUUID
      val tool = Tool(
        toolId, // primary key
        now, // createdAt
        now, // modifiedAt
        userId, // createdBy
        userId, // modifiedBy
        organizationId,
        title,
        description,
        requirements,
        license,
        visibility,
        compatibleDataSources,
        stars
      )

      val toolTagToTools = tags.map(tagId => ToolTagToTool(tagId, toolId))
      val toolCategoryToTools = categories.map(categoryId =>
        ToolCategoryToTool(categoryId, toolId))

      (tool, toolTagToTools, toolCategoryToTools)
    }
  }

  object Create {
    implicit val defaultToolFormat = jsonFormat10(Create.apply _)
  }

  // join of tool/tag/category
  case class TagCategoryJoin(tool: Tool, toolTagId: Option[UUID], toolCategoryId: Option[UUID])
  object TagCategoryJoin {
    def tupled = (TagCategoryJoin.apply _).tupled
    implicit val defaultTagCategoryJoinFormat = jsonFormat3(TagCategoryJoin.apply _)
  }

  /** Tool class when posted with category and tag ids */
  case class WithRelated(
    id: UUID,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    createdBy: String,
    modifiedBy: String,
    organizationId: UUID,
    title: String,
    description: String,
    requirements: String,
    license: String,
    visibility: Visibility,
    compatibleDataSources: List[String] = List.empty,
    stars: Float = 0.0f,
    tags: Seq[UUID],
    categories: Seq[UUID]
  )

  object WithRelated {
    implicit val defaultToolWithRelatedFormat = jsonFormat15(WithRelated.apply)
  }
}
