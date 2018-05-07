package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._

import cats._, cats.data._, cats.effect.IO, cats.implicits._
import com.lonelyplanet.akka.http.extensions.PageRequest

import java.util.{Date, UUID}
import java.sql.Timestamp

import scala.concurrent.Future

object TeamDao extends Dao[Team] {
  val tableName = "teams"

  val selectF = sql"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, organization_id,
      name, settings, is_active
    FROM
  """ ++ tableF

  def getById(teamId: UUID): ConnectionIO[Option[Team]] =
    TeamDao.query.filter(teamId).selectOption

  def unsafeGetById(teamId: UUID): ConnectionIO[Team] =
    TeamDao.query.filter(teamId).select

  def create(
    team: Team
  ): ConnectionIO[Team] = {
    (fr"INSERT INTO" ++ tableF ++ fr"""
      (id, created_at, created_by, modified_at, modified_by, organization_id,
      name, settings, is_active)
    VALUES
      (${team.id}, ${team.createdAt}, ${team.createdBy}, ${team.modifiedAt},
      ${team.modifiedBy}, ${team.organizationId}, ${team.name}, ${team.settings}, true)
    """)
    .update
    .withUniqueGeneratedKeys[Team](
      "id", "created_at", "created_by", "modified_at", "modified_by", "organization_id",
      "name", "settings", "is_active"
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
        "name", "settings", "is_active"
      )
  }

  def userIsMemberF(user: User, teamId: UUID ) = fr"""
    SELECT count(id) > 0
      FROM """ ++ UserGroupRoleDao.tableF ++ fr"""
      WHERE
        user_id = ${user.id} AND
        group_type = ${GroupType.Team.toString}::group_type AND
        group_id = ${teamId} AND
        is_active = true
  """

  def listMembers(teamId: UUID, page: PageRequest): ConnectionIO[PaginatedResponse[User.WithGroupRole]] =
    UserGroupRoleDao.listUsersByGroup(GroupType.Team, teamId, page)

  def validatePath(platformId: UUID,
                   organizationId: UUID,
                   teamId: UUID): ConnectionIO[Boolean] =
    (fr"""
      SELECT count(t.id) > 0
      FROM """ ++ tableF ++ fr""" t
      JOIN """ ++ OrganizationDao.tableF ++ fr""" o
        ON o.id = t.organization_id
      JOIN """ ++ PlatformDao.tableF ++ fr""" p
        ON p.id = o.platform_id
      WHERE
        p.id = ${platformId} AND
        o.id = ${organizationId} AND
        t.id = ${teamId}
    """).query[Boolean].option.map(_.getOrElse(false))

  def userIsMember(user: User, teamId: UUID): ConnectionIO[Boolean] =
    userIsMemberF(user, teamId).query[Boolean].option.map(_.getOrElse(false))

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
          ugr.group_type = ${GroupType.Organization.toString}::group_type AND
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

  def delete(teamId: UUID): ConnectionIO[Int] =
    TeamDao.query.filter(teamId).delete

  def addUserRole(user: User, subject: User, teamId: UUID, userRole: GroupRole): ConnectionIO[UserGroupRole] = {
    val userGroupRoleCreate = UserGroupRole.Create(
      subject, GroupType.Team, teamId, userRole
    )
    UserGroupRoleDao.create(userGroupRoleCreate.toUserGroupRole(user))
  }

  def setUserRole(user: User, subject: User, teamId: UUID, userRole: GroupRole): ConnectionIO[List[UserGroupRole]] = {
    deactivateUserRoles(user, subject, teamId).flatMap(
      deactivatedUserRoles => addUserRole(user, subject, teamId, userRole)
        .map(deactivatedUserRoles ++ List(_))
    )
  }

  def deactivateUserRoles(user: User, subject: User, teamId: UUID): ConnectionIO[List[UserGroupRole]] = {
    val userGroup = UserGroupRole.UserGroup(subject.id, GroupType.Team, teamId)
    UserGroupRoleDao.deactivateUserGroupRoles(userGroup, user)
  }
}
