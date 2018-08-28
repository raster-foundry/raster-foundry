package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel.ToolCategoryToTool

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

object ToolCategoryToToolDao extends Dao[ToolCategoryToTool] {

  val tableName = "tool_categories_to_tools"

  val selectF = sql"""
    SELECT
      tool_category_slug, tool_id
    FROM
  """ ++ tableF
}
