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
  stars: Float = 0.0f,
  definition: Map[String, Any]
) {
  def withRelatedFromComponents(toolTags: Seq[ToolTag], toolCategories: Seq[ToolCategory]):
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
    this.definition,
    toolTags,
    toolCategories
  )

  def withRelatedFromComponentUUIDs(toolTagIds: Seq[UUID], toolCategorySlugs: Seq[String]):
    Tool.WithRelatedUUIDs = Tool.WithRelatedUUIDs(
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
    this.definition,
    toolTagIds,
    toolCategorySlugs
  )
}

/** Case class for tool creation */
object Tool {

  def create = Create.apply _

  def tupled = (Tool.apply _).tupled

  implicit val defaultToolFormat = jsonFormat14(Tool.apply _)

  case class Create(
    organizationId: UUID,
    title: String,
    description: String,
    requirements: String,
    license: String,
    visibility: Visibility,
    compatibleDataSources: List[String],
    stars: Float,
    definition: Map[String, Any],
    tags: Seq[UUID],
    categories: Seq[String]
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
        stars,
        definition
      )

      val toolTagToTools = tags.map(tagId => ToolTagToTool(tagId, toolId))
      val toolCategoryToTools = categories.map(categorySlug =>
        ToolCategoryToTool(categorySlug, toolId))

      (tool, toolTagToTools, toolCategoryToTools)
    }
  }

  object Create {
    implicit val defaultToolFormat = jsonFormat11(Create.apply _)
  }

  // join of tool/tag/category
  case class TagCategoryJoin(tool: Tool, toolTag: Option[ToolTag], toolCategory: Option[ToolCategory])
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
    definition: Map[String, Any],
    tags: Seq[ToolTag],
    categories: Seq[ToolCategory]
  )

  object WithRelated {
    implicit val defaultToolWithRelatedFormat = jsonFormat16(WithRelated.apply)

    def fromRecords(records: Seq[(Tool, Option[ToolTag], Option[ToolCategory])]): Iterable[Tool.WithRelated] = {
      val distinctTools = records.map(_._1).distinct
      val groupedTools = records.groupBy(_._1)
      val tags = records.map(_._2)

      distinctTools map { tool =>
        val (seqTags, seqCategories) = groupedTools(tool).map {
          case (_, tag, category) => (tag, category)
        }.unzip

        tool.withRelatedFromComponents(
          seqTags.flatten,
          seqCategories.flatten
        )
      }
    }
  }

  case class WithRelatedUUIDs(
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
    definition: Map[String, Any],
    tags: Seq[UUID],
    categories: Seq[String]
  )

  object WithRelatedUUIDs {
    implicit val defaultToolWithRelatedFormat = jsonFormat16(WithRelatedUUIDs.apply)
  }
}
