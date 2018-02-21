package com.azavea.rf.database

import java.sql.Timestamp

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.{ToolTag, User}
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import java.util.UUID


object ToolTagDao extends Dao[ToolTag] {

  val tableName = "tool_tags"

  val selectF = sql"""
    SELECT
      id, created_at, modified_at, created_by, modified_by,
      organization_id, tag, owner
    FROM
  """ ++ tableF

  def insert(newTag: ToolTag.Create, user: User): ConnectionIO[ToolTag] = {
    val id = UUID.randomUUID()
    val now = new Timestamp(new java.util.Date().getTime())
    val ownerId = util.Ownership.checkOwner(user, newTag.owner)

    (fr"INSERT INTO" ++ tableF ++
     fr"""
          (id, created_at, modified_at, created_by, modified_by, organization_id, tag, owner)
        VALUES
          (${id}, ${now}, ${now}, ${user.id}, ${user.id}, ${newTag.organizationId}, ${newTag.tag}, ${ownerId})
     """).update.withUniqueGeneratedKeys[ToolTag](
      "id", "created_at", "modified_at", "organization_id", "created_by", "modified_by", "owner", "tag")
  }

  def update(toolTag: ToolTag, id: UUID, user: User): ConnectionIO[Int] = {
    val updateTime = new Timestamp((new java.util.Date()).getTime)
    val idFilter = fr"id = ${id}"

    (fr"UPDATE" ++ tableF ++ fr"""
     SET
       modified_at = ${updateTime},
       modified_by = ${user.id},
       owner = ${toolTag.owner},
       organization_id = ${toolTag.organizationId},
       tag = ${toolTag.tag}
     """ ++ Fragments.whereAndOpt(ownerEditFilter(user), Some(idFilter))).update.run

  }
}

