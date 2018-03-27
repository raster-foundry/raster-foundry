package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

import java.sql.Timestamp
import java.util.UUID

object CategoryDao extends Dao[Category] {

  val tableName = "categories"

  val selectF = sql"""
    SELECT
      slug_label, created_at, modified_at, created_by, modified_by, category
    FROM
  """ ++ tableF

  def insertCategory(category: Category, user: User): ConnectionIO[Category] = {
    (fr"""INSERT INTO categories
        (slug_label, created_at, created_by, modified_at, modified_by, category)
        VALUES
        (${category.slugLabel}, NOW(), ${user.id}, NOW(), ${user.id}, ${category.category})
    """).update.withUniqueGeneratedKeys[Category](
      "slug_label", "created_at", "modified_at", "created_by", "modified_by", "category"
    )
  }

  def deleteCategory(slug: String, user: User): ConnectionIO[Int] = {
    this.query.filter(fr"slug_label = $slug AND created_by = ${user.id}").delete
  }
}

