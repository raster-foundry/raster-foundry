package com.azavea.rf.database

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
import com.lonelyplanet.akka.http.extensions.PageRequest

import java.util.UUID
import java.sql.Timestamp

import scala.concurrent.Future
import com.typesafe.scalalogging.LazyLogging


object OrganizationDao extends Dao[Organization] with LazyLogging {

  val tableName = "organizations"

  val selectF = sql"""
    SELECT
      id, created_at, modified_at, name, platform_id, is_active
    FROM
  """ ++ tableF

  def create(
    org: Organization
  ): ConnectionIO[Organization] = {
    (fr"INSERT INTO" ++ tableF ++ fr"""
          (id, created_at, modified_at, name, platform_id, is_active)
        VALUES
          (${org.id}, ${org.createdAt}, ${org.modifiedAt}, ${org.name}, ${org.platformId}, true)
    """).update.withUniqueGeneratedKeys[Organization](
      "id", "created_at", "modified_at", "name", "platform_id", "is_active"
    )
  }

  def getOrganizationById(id: UUID): ConnectionIO[Option[Organization]] =
    query.filter(id).selectOption

  def unsafeGetOrganizationById(id: UUID): ConnectionIO[Organization] =
    query.filter(id).select

  def createOrganization(newOrg: Organization.Create): ConnectionIO[Organization] = {
    create(newOrg.toOrganization)
  }

  def update(org: Organization, id: UUID): ConnectionIO[Int] = {
    val updateTime = new Timestamp((new java.util.Date()).getTime)

    (fr"UPDATE" ++ tableF ++ fr"""SET
         modified_at = ${updateTime},
         name = ${org.name}
       WHERE id = ${id}
     """).update.run
  }

  def listMembers(organizationId: UUID, page: PageRequest, searchParams: SearchQueryParameters, actingUser: User): ConnectionIO[PaginatedResponse[User.WithGroupRole]] =
    UserGroupRoleDao.listUsersByGroup(GroupType.Organization, organizationId, page, searchParams, actingUser)


  def validatePath(platformId: UUID,
                   organizationId: UUID): ConnectionIO[Boolean] =
    (fr"""
      SELECT count(o.id) > 0
      FROM """ ++ tableF ++ fr""" o
      JOIN """ ++ PlatformDao.tableF ++ fr""" p
        ON p.id = o.platform_id
      WHERE
        p.id = ${platformId} AND
        o.id = ${organizationId}
    """).query[Boolean].option.map(_.getOrElse(false))


  def userIsMemberF(user: User, organizationId: UUID ) = fr"""
    SELECT (
        SELECT is_superuser
        FROM """ ++ UserDao.tableF ++ fr"""
        WHERE id = ${user.id}
      ) OR (
        SELECT count(id) > 0
        FROM """ ++ UserGroupRoleDao.tableF ++ fr"""
        WHERE
          user_id = ${user.id} AND
          group_type = ${GroupType.Organization.toString}::group_type AND
          group_id = ${organizationId} AND
          is_active = true
      ) OR (
      SELECT count(ugr.id) > 0
      FROM""" ++ PlatformDao.tableF ++ fr"""AS p
      JOIN""" ++ tableF ++ fr"""o
        ON o.platform_id = p.id
      JOIN""" ++ UserGroupRoleDao.tableF ++ fr"""ugr
        ON ugr.group_id = p.id
      WHERE
        o.id = ${organizationId} AND
        ugr.user_id = ${user.id} AND
        ugr.group_role = ${GroupRole.Admin.toString}::group_role AND
        ugr.group_type = ${GroupType.Platform.toString}::group_type AND
        ugr.is_active = true
    )
  """

  def userIsMember(user: User, organizationId: UUID): ConnectionIO[Boolean] =
    userIsMemberF(user, organizationId).query[Boolean].option.map(_.getOrElse(false))

  def userIsAdminF(user: User, organizationId: UUID) = fr"""
    SELECT (
        SELECT is_superuser
        FROM """ ++ UserDao.tableF ++ fr"""
        WHERE id = ${user.id}
      ) OR (
      SELECT count(id) > 0
      FROM """ ++ UserGroupRoleDao.tableF ++ fr"""
      WHERE
        user_id = ${user.id} AND
        group_type = ${GroupType.Organization.toString}::group_type AND
        group_role = ${GroupRole.Admin.toString}::group_role AND
        group_id = ${organizationId} AND
        is_active = true
    ) OR (
      SELECT count(ugr.id) > 0
      FROM""" ++ PlatformDao.tableF ++ fr"""AS p
      JOIN""" ++ tableF ++ fr"""o
        ON o.platform_id = p.id
      JOIN""" ++ UserGroupRoleDao.tableF ++ fr"""ugr
        ON ugr.group_id = p.id
      WHERE
        o.id = ${organizationId} AND
        ugr.user_id = ${user.id} AND
        ugr.group_role = ${GroupRole.Admin.toString}::group_role AND
        ugr.group_type = ${GroupType.Platform.toString}::group_type AND
        ugr.is_active = true
    )
  """

  def userIsAdmin(user: User, organizationId: UUID): ConnectionIO[Boolean] =
    userIsAdminF(user, organizationId).query[Boolean].option.map(_.getOrElse(false))

  def getOrgPlatformId(organizationId: UUID): ConnectionIO[UUID] =
    unsafeGetOrganizationById(organizationId) map { _.platformId }

  def addUserRole(actingUser: User, subjectId: String, organizationId: UUID, groupRole: GroupRole): ConnectionIO[UserGroupRole] = {
    val userGroupRoleCreate = UserGroupRole.Create(
      subjectId, GroupType.Organization, organizationId, groupRole
    )
    UserGroupRoleDao.create(userGroupRoleCreate.toUserGroupRole(actingUser))
  }

  def setUserRole(actingUser: User, subjectId: String, organizationId: UUID, groupRole: GroupRole):
      ConnectionIO[List[UserGroupRole]] = {
    for {
      orgRoles <-
        OrganizationDao
          .deactivateUserRoles(
            actingUser, subjectId, organizationId
          ).flatMap(
            (deactivatedRoles) => {
              OrganizationDao.addUserRole(actingUser, subjectId, organizationId, groupRole)
                .map((role) => deactivatedRoles ++ List(role))
            }
          )
      platformId <- getOrgPlatformId(organizationId)
      platformRoles <-
        UserGroupRoleDao
          .listUserGroupRoles(GroupType.Platform, platformId, subjectId)
          .flatMap(
            (roles: List[UserGroupRole]) => roles match {
              case Nil =>
                PlatformDao.setUserRole(actingUser, subjectId, platformId, GroupRole.Member)
              case roles =>
                List.empty[UserGroupRole].pure[ConnectionIO]
            }
          )
    } yield (platformRoles ++ orgRoles)
  }

  def deactivateUserRoles(actingUser: User, subjectId: String, organizationId: UUID): ConnectionIO[List[UserGroupRole]] = {
    val userGroup = UserGroupRole.UserGroup(
      subjectId, GroupType.Organization, organizationId
    )
    UserGroupRoleDao.deactivateUserGroupRoles(userGroup, actingUser)
  }
}
