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

  def createUserGroupRole =
    UserGroupRoleDao.createWithGuard(userIsAdmin, GroupType.Team) _

  def getTeamById(teamId: UUID): ConnectionIO[Option[Team]] =
    TeamDao.query.filter(teamId).selectOption

  def unsafeGetTeamById(teamId: UUID): ConnectionIO[Team] =
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
    """).update
      .withUniqueGeneratedKeys[Team](
        "id",
        "created_at",
        "created_by",
        "modified_at",
        "modified_by",
        "organization_id",
        "name",
        "settings",
        "is_active"
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
    updateQuery.update
      .withUniqueGeneratedKeys[Team](
        "id",
        "created_at",
        "created_by",
        "modified_at",
        "modified_by",
        "organization_id",
        "name",
        "settings",
        "is_active"
      )
  }

  def listOrgTeams(
      organizationId: UUID,
      page: PageRequest,
      qp: TeamQueryParameters): ConnectionIO[PaginatedResponse[Team]] = {
    TeamDao.query
      .filter(fr"organization_id = ${organizationId}")
      .filter(fr"is_active = true")
      .filter(qp)
      .page(page, fr"")
  }

  def listMembers(
      teamId: UUID,
      page: PageRequest,
      searchParams: SearchQueryParameters,
      actingUser: User): ConnectionIO[PaginatedResponse[User.WithGroupRole]] =
    UserGroupRoleDao.listUsersByGroup(
      GroupType.Team,
      teamId,
      page,
      searchParams,
      actingUser,
      Some(fr"ORDER BY ugr.membership_status, ugr.group_role"))

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

  def userIsMemberF(user: User, teamId: UUID) = fr"""
    SELECT (
        SELECT is_superuser
        FROM """ ++ UserDao.tableF ++ fr"""
        WHERE id = ${user.id}
      ) OR (
        SELECT count(id) > 0
        FROM """ ++ UserGroupRoleDao.tableF ++ fr"""
        WHERE
          user_id = ${user.id} AND
          group_type = ${GroupType.Team.toString}::group_type AND
          group_id = ${teamId} AND
          is_active = true
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

  def userIsMember(user: User, teamId: UUID): ConnectionIO[Boolean] =
    userIsMemberF(user, teamId).query[Boolean].option.map(_.getOrElse(false))

  def userIsAdminF(user: User, teamId: UUID) = {
    fr"""
      SELECT (
        SELECT is_superuser
        FROM """ ++ UserDao.tableF ++ fr"""
        WHERE id = ${user.id}
      ) OR (
        SELECT count(id) > 0
        FROM """ ++ UserGroupRoleDao.tableF ++ fr"""
        WHERE
          user_id = ${user.id} AND
          group_type = ${GroupType.Team.toString}::group_type AND
          group_role = ${GroupRole.Admin.toString}::group_role AND
          group_id = ${teamId} AND
          is_active = true  AND
          membership_status = 'APPROVED'
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
          ugr.is_active = true  AND
          membership_status = 'APPROVED'
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
          ugr.is_active = true  AND
          membership_status = 'APPROVED'
      )
    """
  }

  def userIsAdmin(user: User, teamId: UUID) =
    userIsAdminF(user, teamId).query[Boolean].option.map(_.getOrElse(false))

  def delete(teamId: UUID): ConnectionIO[Int] =
    TeamDao.query.filter(teamId).delete

  // TODO: ACR deactivation needs to be reconsidered in issue 4020
  def deactivate(teamId: UUID): ConnectionIO[Int] = {
    for {
      roles <- UserGroupRoleDao.deactivateByGroup(GroupType.Team, teamId)
      teamUpdate <- (fr"UPDATE" ++ tableF ++ fr"""SET
                      is_active = false
                      WHERE id = ${teamId}""").update.run
    } yield teamUpdate
  }

  def addUserRole(platformId: UUID,
                  actingUser: User,
                  subjectId: String,
                  teamId: UUID,
                  groupRole: GroupRole): ConnectionIO[UserGroupRole] = {
    val userGroupRoleCreate = UserGroupRole.Create(
      subjectId,
      GroupType.Team,
      teamId,
      groupRole
    )
    val isSameOrgIO: ConnectionIO[Boolean] = for {
      team <- unsafeGetTeamById(teamId)
      orgId = team.organizationId
      userToAdd <- UserDao.unsafeGetUserById(subjectId)
      userIsOrgMember <- OrganizationDao.userIsMember(userToAdd, orgId)
    } yield { userIsOrgMember }
    createUserGroupRole(teamId,
                        actingUser,
                        subjectId,
                        userGroupRoleCreate,
                        platformId,
                        isSameOrgIO)
  }

  def deactivateUserRoles(actingUser: User,
                          subjectId: String,
                          teamId: UUID): ConnectionIO[List[UserGroupRole]] = {
    val userGroup = UserGroupRole.UserGroup(subjectId, GroupType.Team, teamId)
    UserGroupRoleDao.deactivateUserGroupRoles(userGroup, actingUser)
  }

  def teamsForUser(user: User): ConnectionIO[List[Team]] = {
    for {
      userTeamRoles <- UserGroupRoleDao.listByUserAndGroupType(user,
                                                               GroupType.Team)
      teamIdsO = userTeamRoles.map(_.groupId).toNel
      teams <- teamIdsO match {
        case Some(ids) => query.filter(Fragments.in(fr"id", ids)).list
        case None      => List.empty[Team].pure[ConnectionIO]
      }
    } yield { teams }
  }
}
