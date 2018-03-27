package com.azavea.rf.database

import java.sql.Timestamp

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import java.util.UUID

object TemplateCategoryDao extends Dao[TemplateCategory] {
  val tableName = "template_categories"

  val selectF = sql"""
  SELECT template_id, category_slug FROM
  """ ++ tableF

  def insert(template: Template, category: Category): ConnectionIO[TemplateCategory] = {
    sql"""
       INSERT INTO template_analyses
         (template_id, category_slug)
       VALUES
          (${template.id}, ${category.slugLabel})
       """.update.withUniqueGeneratedKeys[TemplateCategory](
      "template_id", "category_slug"
    )
  }

  def delete(template: Template, category: Category): ConnectionIO[Int] = {
    query.filter(fr"template_id = ${template.id}")
    .filter(fr"category_slug = ${category.slugLabel}")
    .delete
  }

  def getTemplateCategories(template: Template): ConnectionIO[List[Category]] = {
    sql"""
    SELECT categories.* from template_categories tc
    JOIN categories ON tc.category_slug = categories.slug_label
    WHERE tc.template_id = ${template.id}
    """.query[Category].list
  }
}
