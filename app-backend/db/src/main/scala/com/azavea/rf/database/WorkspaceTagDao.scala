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

object WorkspaceTagDao extends Dao[WorkspaceTag] {
  val tableName = "workspace_tags"

  val selectF = sql"""
  SELECT workspace_id, tag_id FROM
  """ ++ tableF

  def insert(workspace: Workspace, tag: Tag): ConnectionIO[WorkspaceTag] = {
    sql"""
       INSERT INTO workspace_tags
         (workspace_id, tag_id)
       VALUES
          (${workspace.id}, ${tag.id})
       """.update.withUniqueGeneratedKeys[WorkspaceTag](
      "workspace_id", "tag_id"
    )
  }

  def delete(workspace: Workspace, tag: Tag): ConnectionIO[Int] = {
    query.filter(fr"workspace_id = ${workspace.id}")
    .filter(fr"tag_id = ${tag.id}")
    .delete
  }

  def createMany(workspace: Workspace, tagList: NonEmptyList[UUID]): ConnectionIO[Int] = {
    val insertQuery = "INSERT INTO workspace_tags (workspace_id, tag_id) VALUES (?, ?)"
    Update[(UUID, UUID)](insertQuery).updateMany(tagList.map(tagId => (workspace.id, tagId)))
  }

  def setWorkspaceTags(workspace: Workspace, tagList: List[UUID]): ConnectionIO[Int] = {
    tagList.toNel match {
      case Some(tags: NonEmptyList[UUID]) =>
        createMany(workspace, tags).flatMap(_ =>
          query
            .filter(fr"workspace_id = ${workspace.id}")
            .filter(Fragments.in(fr"tag_id", tags))
            .delete
        )
      case None =>
        query
          .filter(fr"workspace_id = ${workspace.id}")
          .delete
    }
  }

  def getWorkspaceTags(workspace: Workspace): ConnectionIO[List[Tag]] = {
    sql"""
    SELECT tags.*
    FROM tags JOIN workspace_tags
    WHERE workspace_tags.workspace_id = ${workspace.id}
    """.query[Tag].list
  }
}
