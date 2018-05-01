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

  def userIsAdminF(user: User, organizationId: UUID) = fr"""
    SELECT (
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

  def userIsAdmin(user: User, organizationId: UUID) =
    userIsAdminF(user, organizationId).query[Boolean].option.map(_.getOrElse(false))

  def getOrgPlatformId(organizationId: UUID): ConnectionIO[UUID] = {
    (fr"SELECT platform_id FROM " ++ tableF ++ fr"WHERE id = ${organizationId}").query[UUID].unique
  }

  def addUserRole(user: User, subject: User, organizationId: UUID, userRole: GroupRole): ConnectionIO[UserGroupRole] = {
    val userGroupRoleCreate = UserGroupRole.Create(
      subject, GroupType.Organization, organizationId, userRole
    )
    UserGroupRoleDao.create(userGroupRoleCreate.toUserGroupRole(user))
  }

  def setUserRole(user: User, subject: User, organizationId: UUID, userRole: GroupRole):
      ConnectionIO[List[UserGroupRole]] = {
    for {
      orgRoles <- OrganizationDao.deactivateUserRoles(
        user, subject, organizationId
      ).flatMap(
        (deactivatedRoles) => {
          OrganizationDao.addUserRole(user, subject, organizationId, userRole)
            .map((role) => deactivatedRoles ++ List(role))
        }
      )
      platformId <- getOrgPlatformId(organizationId)
      platformRoles <- UserGroupRoleDao
      .listUserGroupRoles(GroupType.Platform, platformId, subject)
      .flatMap(
        (roles: List[UserGroupRole]) => roles match {
          case Nil =>
            PlatformDao.setUserRole(user, subject, platformId, GroupRole.Member)
          case roles =>
            List.empty[UserGroupRole].pure[ConnectionIO]
        }
      )
    } yield (platformRoles ++ orgRoles)
  }

  def deactivateUserRoles(user: User, removedUser: User, organizationId: UUID): ConnectionIO[List[UserGroupRole]] = {
    val userGroup = UserGroupRole.UserGroup(
      removedUser.id, GroupType.Organization, organizationId
    )
    UserGroupRoleDao.deactivateUserGroupRoles(userGroup, user)
  }
}
