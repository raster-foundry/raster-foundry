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


object ToolRunDao extends Dao[ToolRun] {

  val tableName = "tool_runs"

  val selectF = sql"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, visibility,
      organization, execution_parameters, owner, name
    FROM
  """ ++ tableF

  def insertToolRun(newRun: ToolRun.Create, user: User): ConnectionIO[ToolRun] = {
    val now = new Timestamp(new java.util.Date().getTime())
    val id = UUID.randomUUID()

    sql"""
          INSERT INTO tool_runs
            (id, created_at, created_by, modified_at, modified_by, visibility, organization
             execution_parameters, owner, name)
          VALUES
            (${id}, ${now}, ${user.id}, ${now}, ${user.id}, ${newRun.visibility}, ${newRun.organizationId},
             ${newRun.executionParameters}, ${newRun.owner}, ${newRun.name})
       """.update.withUniqueGeneratedKeys[ToolRun](
      "id", "name", "created_at", "created_by", "modified_at", "modified_by", "owner", "visibility", "organization_id",
      "execution_parameters"
    )
  }

  def updateToolRun(updatedRun: ToolRun, id: UUID, user: User): ConnectionIO[Int] = {
    val now = new Timestamp(new java.util.Date().getTime())
    val idFilter = fr"id = ${id}"

    (sql"""
       UPDATE tool_runs
       SET
         name = ${updatedRun.name},
         modified_at = ${now},
         modified_by = ${user.id},
         visibility = ${updatedRun.visibility},
         execution_paramters = ${updatedRun.executionParameters}
       """ ++ Fragments.whereAndOpt(ownerEditFilter(user), Some(idFilter))).update.run
  }
}

