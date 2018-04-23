package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._

import cats._, cats.data._, cats.effect.IO, cats.implicits._

import java.util.{Date, UUID}
import java.sql.Timestamp

import scala.concurrent.Future

object TeamDao extends Dao[Team] {
  val tableName = "teams"

  val selectF = sql"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, organization_id,
      name, settings
    FROM
  """ ++ tableF

  def create(
    team: Team
  ): ConnectionIO[Team] = {
    (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, created_at, created_by, modified_at, modified_by, organization_id,
      name, settings)
    VALUES
      (${team.id}, ${team.createdAt}, ${team.createdBy}, ${team.modifiedAt},
      ${team.modifiedBy}, ${team.organizationId}, ${team.name}, ${team.settings})
    """)
    .update
    .withUniqueGeneratedKeys[Team](
      "id", "created_at", "created_by", "modified_at", "modified_by", "organization_id",
      "name", "settings"
    )
  }

  def update(
    team: Team,
    id: UUID,
    user: User
  ): ConnectionIO[Team] = {
    val now = new Timestamp((new java.util.Date()).getTime())
    val updateQuery =
      fr"UPDATE" ++ this.tableF ++
      fr"""
      SET modified_at = ${now},
          modified_by = ${user.id},
          name = ${team.name},
          settings = ${team.settings}
      WHERE id = ${id}"""
    updateQuery
      .update
      .withUniqueGeneratedKeys[Team](
        "id", "created_at", "created_by", "modified_at", "modified_by", "organization_id",
        "name", "settings"
      )
  }
}
