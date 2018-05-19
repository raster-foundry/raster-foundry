  package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.filter.Filters._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import com.lonelyplanet.akka.http.extensions.PageRequest
import io.circe._

import java.util.UUID

object UserGroupRoleDao extends Dao[UserGroupRole] {
  val tableName = "user_group_roles"


  val selectF =
    fr"""
      SELECT
        id, created_at, created_by,
        modified_at, modified_by, is_active,
        user_id, group_type, group_id, group_role
      FROM
    """ ++ tableF

  def createF(ugr: UserGroupRole) =
    fr"INSERT INTO" ++ tableF ++ fr"""(
        id, created_at, created_by,
        modified_at, modified_by, is_active,
        user_id, group_type, group_id, group_role
      ) VALUES (
          ${ugr.id}, ${ugr.createdAt}, ${ugr.createdBy},
          ${ugr.modifiedAt}, ${ugr.modifiedBy}, ${ugr.isActive},
          ${ugr.userId}, ${ugr.groupType}, ${ugr.groupId}, ${ugr.groupRole}
      )
    """

  // We don't want to support changes to roles beyond deactivation and promotion
  def updateF(ugr: UserGroupRole, id: UUID, user: User) =
    fr"UPDATE" ++ tableF ++ fr"""SET
          modified_at = NOW(),
          modified_by = ${user.id},
          is_active = ${ugr.isActive},
          group_role = ${ugr.groupRole}
          where id = ${id}
        """

  def getUserGroupRoleById(ugrId: UUID, user: User): ConnectionIO[Option[UserGroupRole]] =
    query.filter(ugrId).filter(fr"user_id = ${user.id}").selectOption

  def unsafeGetUserGroupRoleById(ugrId: UUID, user: User): ConnectionIO[UserGroupRole] =
    query.filter(ugrId).filter(fr"user_id = ${user.id}").select

  def create(ugr: UserGroupRole): ConnectionIO[UserGroupRole] = {
    val isValidGroupIO = ugr.groupType match {
      case GroupType.Platform => PlatformDao.query.filter(ugr.groupId).exists
      case GroupType.Organization => OrganizationDao.query.filter(ugr.groupId).exists
      case GroupType.Team => TeamDao.query.filter(ugr.groupId).exists
    }

    val create = createF(ugr).update.withUniqueGeneratedKeys[UserGroupRole](
      "id", "created_at", "created_by",
      "modified_at", "modified_by", "is_active",
      "user_id", "group_type", "group_id", "group_role"
    )

    for {
      isValid <- isValidGroupIO
      createUGR <- {
        if (isValid) create
        else throw new Exception(s"Invalid group: ${ugr.groupType}(${ugr.groupId})")
      }
    } yield createUGR
  }

  def getOption(id: UUID): ConnectionIO[Option[UserGroupRole]] = {
    query.filter(id).selectOption
  }

  // List roles that a given user has been granted
  def listByUser(user: User): ConnectionIO[List[UserGroupRole]] = {
    query
      .filter(fr"user_id = ${user.id}")
      .filter(fr"is_active = true")
      .list
  }

  // List roles that have been given to users for a group
  def listByGroup(groupType: GroupType, groupId: UUID): ConnectionIO[List[UserGroupRole]] = {
    query
      .filter(fr"group_type = ${groupType}")
      .filter(fr"group_id = ${groupId}")
      .filter(fr"is_active = true")
      .list
  }

  // List a user's roles in a group
  def listUserGroupRoles(groupType: GroupType, groupId: UUID, userId: String): ConnectionIO[List[UserGroupRole]] = {
    query.filter(fr"group_type = ${groupType}")
      .filter(fr"group_id = ${groupId}")
      .filter(fr"user_id = ${userId}")
      .filter(fr"is_active = true")
      .list
  }

  def listUsersByGroup(groupType: GroupType, groupId: UUID, page: PageRequest, searchParams: SearchQueryParameters, actingUser: User): ConnectionIO[PaginatedResponse[User.WithGroupRole]] = {
    // PUBLIC users can be seen by anyone within the same platform
    // PRIVATE users can be seen by anyone within the same organization
    // PRIVATE users can be seen by anyone within the same team (even if orgs are different)
    val sf =
      fr"""
        SELECT u.id, u.role, u.created_at, u.modified_at,
          u.dropbox_credential, u.planet_credential, u.email_notifications,
          u.email, u.name, u.profile_image_uri, u.is_superuser, u.is_active, u.visibility,
          ugr.group_role
        FROM """ ++ tableF ++ fr""" ugr
        JOIN """ ++ UserDao.tableF ++ fr""" u
          ON u.id = ugr.user_id
      """

    val cf =
      fr"""
        SELECT count(ugr.id)
        FROM """ ++ tableF ++ fr"""AS ugr
        JOIN """ ++ UserDao.tableF ++ fr""" u
          ON u.id = ugr.user_id
      """

    // Filter that applies the following rules:
    // 1) A user is always allowed to list themselves
    // 2) A public user is always visible
    // 3) A private user is only visible to those within the same organization or team

    val ff =
      fr"""(
        u.id = ${actingUser.id} OR
        u.visibility = ${UserVisibility.Public.toString}::user_visibility OR
        u.id IN (
          SELECT A.user_id
          FROM """ ++ tableF ++ fr""" A
          JOIN """ ++ tableF ++ fr""" B
            ON A.group_type = B.group_type AND
              A.group_id = B.group_id
          WHERE
            B.user_id = ${actingUser.id} AND
            (
              B.group_type = ${GroupType.Organization.toString}::group_type OR
              B.group_type = ${GroupType.Team.toString}::group_type
            )
        )
      )"""

    if (actingUser.isSuperuser) {
      query
        .filter(fr"ugr.group_type = ${groupType}")
        .filter(fr"ugr.group_id = ${groupId}")
        .filter(fr"ugr.is_active = true")
        .filter(searchQP(searchParams, List("u.name", "u.email")))
        .page[User.WithGroupRole](page, sf, cf)
    } else {
      query
        .filter(fr"ugr.group_type = ${groupType}")
        .filter(fr"ugr.group_id = ${groupId}")
        .filter(fr"ugr.is_active = true")
        .filter(ff)
        .filter(searchQP(searchParams, List("u.name", "u.email")))
        .page[User.WithGroupRole](page, sf, cf)
    }
  }

  // @TODO: ensure a user cannot demote (or promote?) themselves
  def update(ugr: UserGroupRole, id: UUID, user: User): ConnectionIO[Int] =
    updateF(ugr, id, user).update.run

  def deactivate(id: UUID, user: User): ConnectionIO[Int] = {
    (fr"UPDATE" ++ tableF ++ fr""" SET
          is_active = false,
          modified_at = NOW(),
          modified_by = ${user.id}
            WHERE id = ${id}
        """).update.run
  }

  def deactivateUserGroupRoles(ugr: UserGroupRole.UserGroup, user: User): ConnectionIO[List[UserGroupRole]] = {
    (fr"UPDATE" ++ tableF ++ fr"""SET
        modified_at = NOW(),
        modified_by = ${user.id},
        is_active = false
        """ ++ Fragments.whereAnd(
      fr"user_id = ${ugr.userId}",
      fr"group_type = ${ugr.groupType}",
      fr"group_id = ${ugr.groupId}",
      fr"is_active = true"
     )
    ).update.withGeneratedKeys[UserGroupRole](
      "id",
      "created_at",
      "created_by",
      "modified_at",
      "modified_by",
      "is_active",
      "user_id",
      "group_type",
      "group_id",
      "group_role"
    ).compile.toList
  }
}
