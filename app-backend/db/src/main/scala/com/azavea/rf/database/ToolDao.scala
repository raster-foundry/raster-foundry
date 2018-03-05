package com.azavea.rf.database

import java.sql.Timestamp

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.{ToolRun, User}
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import java.util.UUID

import scala.concurrent.Future

import com.azavea.rf.datamodel.Tool

object ToolDao extends Dao[Tool] {
  val tableName = "tools"

  val selectF = sql"""
    SELECT
      id, created_at, modified_at, created_by, modified_by, owner, organization_id, title,
      description, requirements, license, visibility, compatible_data_sources, stars, definition
    FROM """ ++ tableF

  def insert(newTool: Tool.Create, user: User): ConnectionIO[Tool] = {
    val id = UUID.randomUUID()
    val now = new Timestamp(new java.util.Date().getTime())
    val ownerId = util.Ownership.checkOwner(user, newTool.owner)

    sql"""
       INSERT INTO tools
         (id, created_at, modified_at, created_by, modified_by, owner, organization_id, title,
          description, requirements, license, visibility, compatible_data_sources, stars, definition)
       VALUES
         (${id}, ${now}, ${now}, ${user.id}, ${user.id}, ${ownerId}, ${newTool.organizationId}, ${newTool.title},
          ${newTool.description}, ${newTool.requirements}, ${newTool.license}, ${newTool.visibility},
          ${newTool.compatibleDataSources}, ${newTool.stars}, ${newTool.definition})
       """.update.withUniqueGeneratedKeys[Tool](
      "id", "created_at", "modified_at", "created_by", "modified_by", "owner", "organization_id", "title",
      "description", "requirements", "license", "visibility", "compatible_data_sources", "stars", "definition"
    )
  }

  def update(tool: Tool, id: UUID, user: User): ConnectionIO[Int] = {
    val updateTime = new Timestamp(new java.util.Date().getTime())
    val idFilter = fr"id = ${id}"
    (sql"""
       UPDATE tools
       SET
         modified_by = ${user.id},
         modified_at = ${updateTime},
         title = ${tool.title},
         description = ${tool.description},
         requirements = ${tool.requirements},
         license = ${tool.license},
         visibility = ${tool.visibility},
         compatible_data_sources = ${tool.compatibleDataSources},
         stars = ${tool.stars},
         definition = ${tool.definition}
     """ ++ Fragments.whereAndOpt(ownerEditFilter(user), Some(idFilter))).update.run
  }
}
