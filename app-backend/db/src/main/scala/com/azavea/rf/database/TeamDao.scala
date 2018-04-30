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
          name = ${team.name}
          settings = ${team.settings}
      WHERE id = ${id}"""
    updateQuery
      .update
      .withUniqueGeneratedKeys[Team](
        "id", "created_at", "created_by", "modified_at", "modified_by", "organization_id",
        "name", "settings"
      )
  }

  def userIsAdminF(user: User, teamId: UUID) = {
    fr"""
      SELECT (
        SELECT count(id) > 0
        FROM """ ++ UserGroupRoleDao.tableF ++ fr"""
        WHERE
          user_id = ${user.id} AND
          group_type = ${GroupType.Team.toString}::group_type AND
          group_role = ${GroupRole.Admin.toString}::group_role AND
          group_id = ${teamId} AND
          is_active = true
        LIMIT 1
      ) OR (
        SELECT count(ugr.id) > 0
        FROM""" ++ OrganizationDao.tableF ++ fr"""o
        JOIN""" ++ tableF ++ fr"""t
          ON t.organization_id = o.id
        JOIN""" ++ UserGroupRoleDao.tableF ++ fr"""ugr
          ON ugr.group_id = o.id
        WHERE
          t.id = ${teamId} AND
          ugr.user_id = ${user.id} AND
          ugr.group_role = ${GroupRole.Admin.toString}::group_role AND
          ugr.group_type = ${GroupType.Platform.toString}::group_type AND
          ugr.is_active = true
      ) OR (
        SELECT count(ugr.id) > 0
        FROM""" ++ PlatformDao.tableF ++ fr"""AS p
        JOIN""" ++ OrganizationDao.tableF ++ fr"""o
          ON o.platform_id = p.id
        JOIN""" ++ tableF ++ fr"""t
          ON t.organization_id = o.id
        JOIN""" ++ UserGroupRoleDao.tableF ++ fr"""ugr
          ON ugr.group_id = p.id
        WHERE
          t.id = ${teamId} AND
          ugr.user_id = ${user.id} AND
          ugr.group_role = ${GroupRole.Admin.toString}::group_role AND
          ugr.group_type = ${GroupType.Platform.toString}::group_type AND
          ugr.is_active = true
      )
    """
  }

  def userIsAdmin(user: User, teamId: UUID) =
    userIsAdminF(user, teamId).query[Boolean].option.map(_.getOrElse(false))
}
