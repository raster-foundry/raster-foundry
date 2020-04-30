package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.{Order, PageRequest}

import cats._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.circe.jsonb.implicits._
import doobie.postgres.implicits._

import java.sql.Timestamp
import java.util.UUID

object TeamDao extends Dao[Team] {
  val tableName = "teams"

  val selectF = sql"""
    SELECT
      id, created_at, created_by, modified_at, organization_id,
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
      (id, created_at, created_by, modified_at, organization_id,
      name, settings, is_active)
    VALUES
      (${team.id}, ${team.createdAt}, ${team.createdBy}, ${team.modifiedAt},
      ${team.organizationId}, ${team.name}, ${team.settings}, true)
    """).update
      .withUniqueGeneratedKeys[Team](
        "id",
        "created_at",
        "created_by",
        "modified_at",
        "organization_id",
        "name",
        "settings",
        "is_active"
      )
  }

  def update(
      team: Team,
      id: UUID
  ): ConnectionIO[Team] = {
    val now = new Timestamp((new java.util.Date()).getTime())
    val updateQuery =
      fr"UPDATE" ++ this.tableF ++
        fr"""
      SET modified_at = ${now},
          name = ${team.name},
          settings = ${team.settings}
      WHERE id = ${id}"""
    updateQuery.update
      .withUniqueGeneratedKeys[Team](
        "id",
        "created_at",
        "created_by",
        "modified_at",
        "organization_id",
        "name",
        "settings",
        "is_active"
      )
  }

  def createWithRole(team: Team, user: User): ConnectionIO[Team] = {
    for {
      createdTeam <- create(team)
      userGroupRoleCreate = UserGroupRole.Create(
        user.id,
        GroupType.Team,
        team.id,
        GroupRole.Admin
      )
      _ <- UserGroupRoleDao.create(
        userGroupRoleCreate.toUserGroupRole(user, MembershipStatus.Approved)
      )
    } yield createdTeam
  }

  def listOrgTeams(
      organizationId: UUID,
      page: PageRequest,
      qp: TeamQueryParameters
  ): ConnectionIO[PaginatedResponse[Team]] = {
    TeamDao.query
      .filter(fr"organization_id = ${organizationId}")
      .filter(fr"is_active = true")
      .filter(qp)
      .page(page)
  }

  def listMembers(
      teamId: UUID,
      page: PageRequest,
      searchParams: SearchQueryParameters,
      actingUser: User
  ): ConnectionIO[PaginatedResponse[User.WithGroupRole]] =
    UserGroupRoleDao.listUsersByGroup(
      GroupType.Team,
      teamId,
      page,
      searchParams,
      actingUser,
      Some(
        Map("ugr.membership_status" -> Order.Asc, "ugr.group_role" -> Order.Asc)
      )
    )

  def validatePath(
      platformId: UUID,
      organizationId: UUID,
      teamId: UUID
  ): ConnectionIO[Boolean] =
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
      _ <- UserGroupRoleDao.deactivateByGroup(GroupType.Team, teamId)
      teamUpdate <- (fr"UPDATE" ++ tableF ++ fr"""SET
                      is_active = false
                      WHERE id = ${teamId}""").update.run
    } yield teamUpdate
  }

  def addUserRole(
      platformId: UUID,
      actingUser: User,
      subjectId: String,
      teamId: UUID,
      groupRole: GroupRole
  ): ConnectionIO[UserGroupRole] = {
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
    createUserGroupRole(
      teamId,
      actingUser,
      subjectId,
      userGroupRoleCreate,
      platformId,
      isSameOrgIO
    )
  }

  def deactivateUserRoles(
      subjectId: String,
      teamId: UUID
  ): ConnectionIO[List[UserGroupRole]] = {
    val userGroup = UserGroupRole.UserGroup(subjectId, GroupType.Team, teamId)
    UserGroupRoleDao.deactivateUserGroupRoles(userGroup)
  }

  def teamsForUser(user: User): ConnectionIO[List[Team]] = {
    for {
      userTeamRoles <- UserGroupRoleDao.listByUserAndGroupType(
        user,
        GroupType.Team
      )
      teamIdsO = userTeamRoles.map(_.groupId).toNel
      teams <- teamIdsO match {
        case Some(ids) => query.filter(Fragments.in(fr"id", ids)).list
        case None      => List.empty[Team].pure[ConnectionIO]
      }
    } yield { teams }
  }
}
