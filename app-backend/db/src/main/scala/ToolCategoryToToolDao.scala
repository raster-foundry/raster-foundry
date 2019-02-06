package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel.ToolCategoryToTool

import doobie.implicits._
import doobie.postgres.implicits._

object ToolCategoryToToolDao extends Dao[ToolCategoryToTool] {

  val tableName = "tool_categories_to_tools"

  val selectF = sql"""
    SELECT
      tool_category_slug, tool_id
    FROM
  """ ++ tableF
}
