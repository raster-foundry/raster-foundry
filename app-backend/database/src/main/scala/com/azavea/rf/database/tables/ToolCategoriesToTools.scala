package com.azavea.rf.database.tables

import java.util.UUID

import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._

class ToolCategoriesToTools(_tableTag: Tag) extends Table[ToolCategoryToTool](_tableTag, "tool_categories_to_tools") {
  def * = (toolCategorySlug, toolId) <> (ToolCategoryToTool.tupled, ToolCategoryToTool.unapply)

  val toolCategorySlug: Rep[String] = column[String]("tool_category_slug")
  val toolId: Rep[UUID] = column[UUID]("tool_id")

  val pk = primaryKey("tool_categories_to_tools_pkey", (toolCategorySlug, toolId))

  lazy val toolsFk = foreignKey("tool_categories_to_tools_tool_id_fkey", toolId, Tools)(r =>
    r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  lazy val toolCategoriesFk = foreignKey("tool_categories_to_tools_tool_category_slug_fkey", toolCategorySlug, ToolCategories)(r =>
    r.slugLabel, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
}

object ToolCategoriesToTools extends TableQuery(tag => new ToolCategoriesToTools(tag))
