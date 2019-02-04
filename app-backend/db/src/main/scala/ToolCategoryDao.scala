package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel.{ToolCategory, User}

import doobie._, doobie.implicits._


object ToolCategoryDao extends Dao[ToolCategory] {

  val tableName = "tool_categories"

  val selectF = sql"""
    SELECT
      slug_label, created_at, modified_at, created_by, modified_by, category
    FROM
  """ ++ tableF

  def insertToolCategory(category: ToolCategory,
                         user: User): ConnectionIO[ToolCategory] = {
    (fr"""INSERT INTO tool_categories
        (slug_label, created_at, created_by, modified_at, modified_by, category)
        VALUES
        (${category.slugLabel}, NOW(), ${user.id}, NOW(), ${user.id}, ${category.category})
    """).update.withUniqueGeneratedKeys[ToolCategory](
      "slug_label",
      "created_at",
      "modified_at",
      "created_by",
      "modified_by",
      "category"
    )
  }

  def deleteToolCategory(slug: String, user: User): ConnectionIO[Int] = {
    this.query.filter(fr"slug_label = $slug AND created_by = ${user.id}").delete
  }
}
