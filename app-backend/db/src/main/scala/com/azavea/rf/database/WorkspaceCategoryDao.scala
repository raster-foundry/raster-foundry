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

object WorkspaceCategoryDao extends Dao[WorkspaceCategory] {
  val tableName = "workspace_categories"

  val selectF = sql"""
  SELECT workspace_id, category_slug FROM
  """ ++ tableF

  def insert(workspace: Workspace, category: Category): ConnectionIO[WorkspaceCategory] = {
    sql"""
       INSERT INTO workspace_analyses
         (workspace_id, category_slug)
       VALUES
          (${workspace.id}, ${category.slugLabel})
       """.update.withUniqueGeneratedKeys[WorkspaceCategory](
      "workspace_id", "category_slug"
    )
  }

  def delete(workspace: Workspace, category: Category): ConnectionIO[Int] = {
    query.filter(fr"workspace_id = ${workspace.id}")
    .filter(fr"category_slug = ${category.slugLabel}")
    .delete
  }

  def getWorkspaceCategories(workspace: Workspace): ConnectionIO[List[Category]] = {
    sql"""
    SELECT categories.* from workspace_categories wc
    JOIN categories ON wc.category_slug = categories.slug_label
    WHERE wc.workspace_id = ${workspace.id}
    """.query[Category].list
  }
}
