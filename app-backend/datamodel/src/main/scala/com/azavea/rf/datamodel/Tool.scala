package com.azavea.rf.datamodel

import io.circe._

import java.util.UUID
import java.sql.Timestamp

/** Model Lab Tool */
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
  definition: Json
) {
  def withRelatedFromComponents(toolTags: Seq[ToolTag], toolCategories: Seq[ToolCategory], organization: Option[Organization]):
      Tool.WithRelated = Tool.WithRelated(
    this.id,
    this.createdAt,
    this.modifiedAt,
    this.createdBy,
    this.modifiedBy,
    organization,
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

  case class Create(
    organizationId: UUID,
    title: String,
    description: String,
    requirements: String,
    license: String,
    visibility: Visibility,
    compatibleDataSources: List[String],
    stars: Float,
    definition: Json,
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

  // join of tool/tag/category
  case class ToolRelationshipJoin(tool: Tool, toolTag: Option[ToolTag], toolCategory: Option[ToolCategory], organization: Option[Organization])

  object ToolRelationshipJoin {
    def tupled = (ToolRelationshipJoin.apply _).tupled
  }

  /** Tool class when posted with category and tag ids */
  case class WithRelated(
    id: UUID,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    createdBy: String,
    modifiedBy: String,
    organization: Option[Organization],
    title: String,
    description: String,
    requirements: String,
    license: String,
    visibility: Visibility,
    compatibleDataSources: List[String] = List.empty,
    stars: Float = 0.0f,
    definition: Json,
    tags: Seq[ToolTag],
    categories: Seq[ToolCategory]
  )

  object WithRelated {

    def fromRecords(records: Seq[(Tool, Option[ToolTag], Option[ToolCategory], Option[Organization])]): Iterable[Tool.WithRelated] = {
      val distinctTools = records.map(_._1).distinct
      val groupedTools = records.groupBy(_._1)

      distinctTools map { tool =>
        val (seqTags, seqCategories, seqOrganizations) = groupedTools(tool).map {
          case (_, tag, category, organization) => (tag, category, organization)
        }.unzip3

        tool.withRelatedFromComponents(
          seqTags.flatten,
          seqCategories.flatten,
          seqOrganizations.flatten.headOption
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
    definition: Json,
    tags: Seq[UUID],
    categories: Seq[String]
  )
}

