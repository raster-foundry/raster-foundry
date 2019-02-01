package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.common.datamodel.ToolCategoryToTool

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
