package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import io.circe._

import com.lonelyplanet.akka.http.extensions.PageRequest

import java.util.UUID

object PlatformDao extends Dao[Platform] {
  val tableName = "platforms"

  val selectF = sql"""
    SELECT
      id, name, settings, is_active, default_organization_id
    FROM
  """ ++ tableF

  def createF(platform: Platform) = fr"INSERT INTO" ++ tableF ++ fr"""(
        id, name, settings, is_active, default_organization_id
      )
      VALUES (
        ${platform.id}, ${platform.name}, ${platform.settings}, ${platform.isActive}, ${platform.defaultOrganizationId}
      )
  """

  def getPlatformById(platformId: UUID) =
    query.filter(platformId).selectOption

  def unsafeGetPlatformById(platformId: UUID) =
    query.filter(platformId).select

  def listPlatforms(page: PageRequest) =
    query.page(page)

  def listMembers(platformId: UUID, page: PageRequest, searchParams: SearchQueryParameters, actingUser: User): ConnectionIO[PaginatedResponse[User.WithGroupRole]] =
    UserGroupRoleDao.listUsersByGroup(GroupType.Platform, platformId, page, searchParams, actingUser)

  def create(platform: Platform): ConnectionIO[Platform] = {
    createF(platform).update.withUniqueGeneratedKeys[Platform](
      "id", "name", "settings", "is_active", "default_organization_id"
    )
  }

  def update(platform: Platform, id: UUID, user: User): ConnectionIO[Int] = {
      (fr"UPDATE" ++ tableF ++ fr"""SET
        name = ${platform.name},
        settings = ${platform.settings},
        default_organization_id = ${platform.defaultOrganizationId}
        where id = ${id}
      """).update.run
  }

  def validatePath(platformId: UUID): ConnectionIO[Boolean] =
  (fr"""
    SELECT count(p.id) > 0
    FROM """ ++ tableF ++ fr""" p
    WHERE p.id = ${platformId}
  """).query[Boolean].option.map(_.getOrElse(false))

  def userIsMemberF(user: User, platformId: UUID ) = fr"""
    SELECT (
        SELECT is_superuser
        FROM """ ++ UserDao.tableF ++ fr"""
        WHERE id = ${user.id}
      ) OR (
        SELECT count(id) > 0
        FROM """ ++ UserGroupRoleDao.tableF ++ fr"""
        WHERE
          user_id = ${user.id} AND
          group_type = ${GroupType.Platform.toString}::group_type AND
          group_id = ${platformId} AND
          is_active = true
      )
  """

  def userIsMember(user: User, platformId: UUID): ConnectionIO[Boolean] =
    userIsMemberF(user, platformId).query[Boolean].option.map(_.getOrElse(false))


  def userIsAdminF(user: User, platformId: UUID) = fr"""
      SELECT (
        SELECT is_superuser
        FROM """ ++ UserDao.tableF ++ fr"""
        WHERE id = ${user.id}
      ) OR (
        SELECT count(id) > 0
        FROM """ ++ UserGroupRoleDao.tableF ++ fr"""
        WHERE
          user_id = ${user.id} AND
          group_type = ${GroupType.Platform.toString}::group_type AND
          group_role = ${GroupRole.Admin.toString}::group_role AND
          group_id = ${platformId} AND
          is_active = true
      )
  """

  def userIsAdmin(user: User, platformId: UUID) =
    userIsAdminF(user, platformId).query[Boolean].option.map(_.getOrElse(false))

  def delete(platformId: UUID): ConnectionIO[Int] =
    PlatformDao.query.filter(platformId).delete

  def addUserRole(actingUser: User, subjectId: String, platformId: UUID, userRole: GroupRole): ConnectionIO[UserGroupRole] = {
    val userGroupRoleCreate = UserGroupRole.Create(
      subjectId, GroupType.Platform, platformId, userRole
    )
    UserGroupRoleDao.create(userGroupRoleCreate.toUserGroupRole(actingUser))
  }

  def setUserRole(actingUser: User, subjectId: String, platformId: UUID, userRole: GroupRole):
      ConnectionIO[List[UserGroupRole]] = {
    deactivateUserRoles(actingUser, subjectId, platformId)
      .flatMap(updatedRoles =>
        addUserRole(actingUser, subjectId, platformId, userRole).map((ugr: UserGroupRole) => updatedRoles ++ List(ugr))
      )
  }

  def deactivateUserRoles(actingUser: User, subjectId: String, platformId: UUID): ConnectionIO[List[UserGroupRole]] = {
    val userGroup = UserGroupRole.UserGroup(subjectId, GroupType.Platform, platformId)
    UserGroupRoleDao.deactivateUserGroupRoles(userGroup, actingUser)
  }
}
