package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.{ ToolCategory, User }

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

import java.sql.Timestamp
import java.util.UUID


object ToolCategoryDao extends Dao[ToolCategory] {

  val tableName = "tool_categories"

  val selectF = sql"""
    SELECT
      slug_label, created_at, modified_at, created_by, modified_by, category
    FROM
  """ ++ tableF

  def insertToolCategory(category: ToolCategory, user: User) = {
    val id = UUID.randomUUID
    (fr"INSERT INTO" ++ tableF ++ fr"""
        (slug_label, created_at, created_by, modified_at, modified_by, category)
        VALUE
        (${category.slugLabel}, NOW(), ${user.id}, now(), ${user.id}, ${category.category})
    """).update.withUniqueGeneratedKeys[ToolCategory](
      "slug_label", "created_at", "created_by", "modified_at", "modified_by", "category"
    )
  }
}

