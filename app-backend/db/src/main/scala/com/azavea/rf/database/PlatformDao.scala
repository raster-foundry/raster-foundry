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
      id, created_at, created_by, modified_at, modified_by, name, settings, is_active
    FROM
  """ ++ tableF

  def createF(platform: Platform) = fr"INSERT INTO" ++ tableF ++ fr"""(
        id, created_at, created_by,
        modified_at, modified_by, name,
        settings, is_active
      )
      VALUES (
        ${platform.id}, ${platform.createdAt}, ${platform.createdBy},
        ${platform.modifiedAt}, ${platform.modifiedBy}, ${platform.name},
        ${platform.settings}, true
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
      "id", "created_at", "created_by", "modified_at", "modified_by", "name", "settings", "is_active"
    )
  }

  def update(platform: Platform, id: UUID, user: User): ConnectionIO[Int] = {
      (fr"UPDATE" ++ tableF ++ fr"""SET
        name = ${platform.name},
        modified_at = NOW(),
        modified_by = ${user.id},
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

}
