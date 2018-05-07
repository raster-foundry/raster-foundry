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
      id, name, settings, is_active
    FROM
  """ ++ tableF

  def createF(platform: Platform) = fr"INSERT INTO" ++ tableF ++ fr"""(
        id, name, settings, is_active
      )
      VALUES (
        ${platform.id}, ${platform.name}, ${platform.settings}, ${platform.isActive}
      )
  """

  def getPlatformById(platformId: UUID) =
    query.filter(platformId).selectOption

  def unsafeGetPlatformById(platformId: UUID) =
    query.filter(platformId).select

  def listPlatforms(page: PageRequest) =
    query.page(page)

  def create(platform: Platform): ConnectionIO[Platform] = {
    createF(platform).update.withUniqueGeneratedKeys[Platform](
      "id", "name", "settings", "is_active"
    )
  }

  def update(platform: Platform, id: UUID, user: User): ConnectionIO[Int] = {
      (fr"UPDATE" ++ tableF ++ fr"""SET
        name = ${platform.name},
        settings = ${platform.settings}
        where id = ${id}
      """).update.run
  }


  def userIsAdminF(user: User, platformId: UUID) = fr"""
      SELECT (
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

  def addUserRole(user: User, subject: User, platformId: UUID, userRole: GroupRole): ConnectionIO[UserGroupRole] = {
    val userGroupRoleCreate = UserGroupRole.Create(
      subject, GroupType.Platform, platformId, userRole
    )
    UserGroupRoleDao.create(userGroupRoleCreate.toUserGroupRole(user))
  }

  def setUserRole(user: User, subject: User, platformId: UUID, userRole: GroupRole):
      ConnectionIO[List[UserGroupRole]] = {
    deactivateUserRoles(user, subject, platformId)
      .flatMap(updatedRoles =>
        addUserRole(user, subject, platformId, userRole).map((ugr: UserGroupRole) => updatedRoles ++ List(ugr))
      )
  }

  def deactivateUserRoles(user: User, subject: User, platformId: UUID): ConnectionIO[List[UserGroupRole]] = {
    val userGroup = UserGroupRole.UserGroup(subject.id, GroupType.Platform, platformId)
    UserGroupRoleDao.deactivateUserGroupRoles(userGroup, user)
  }
}
