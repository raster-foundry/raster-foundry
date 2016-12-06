package com.azavea.rf.database.tables

import java.util.UUID

import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel._

class ToolTagsToTools(_tableTag: Tag) extends Table[ToolTagToTool](_tableTag, "tool_tags_to_tools") {
  def * = (toolTagId, toolId) <> (ToolTagToTool.tupled, ToolTagToTool.unapply)

  val toolTagId: Rep[UUID] = column[UUID]("tool_tag_id")
  val toolId: Rep[UUID] = column[UUID]("tool_id")

  val pk = primaryKey("tool_tags_to_tools_pkey", (toolTagId, toolId))

  lazy val toolsFk = foreignKey("tool_tags_to_tools_tool_id_fkey", toolId, Tools)(r =>
    r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  lazy val toolTagsFk = foreignKey("tool_tags_to_tools_tool_tag_id_fkey", toolTagId, ToolTags)(r =>
    r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
}

object ToolTagsToTools extends TableQuery(tag => new ToolTagsToTools(tag))
