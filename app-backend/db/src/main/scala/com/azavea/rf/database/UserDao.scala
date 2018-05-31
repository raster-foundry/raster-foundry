package com.azavea.rf.database

import java.sql.Timestamp

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.{User, UserRole, Credential, UserVisibility}
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import java.util.UUID

import scala.concurrent.Future

object UserDao extends Dao[User] {

  val tableName = "users"

  val selectF = sql"""
    SELECT
      id, role, created_at, modified_at,
      dropbox_credential, planet_credential, email_notifications,
      email, name, profile_image_uri, is_superuser, is_active, visibility
    FROM
  """ ++ tableF

  def filterById(id: String) = {
    query.filter(fr"id = ${id}")
  }

  def unsafeGetUserById(id: String): ConnectionIO[User] = {
    filterById(id).select
  }

  def getUserById(id: String): ConnectionIO[Option[User]] = {
    filterById(id).selectOption
  }

  def getUserAndActiveRolesById(id: String): ConnectionIO[UserOptionAndRoles] = {
    for {
      user <- getUserById(id)
      roles <- {
        user match {
          case Some(u) =>
            UserGroupRoleDao.listByUser(u)
          case _ =>
            List.empty[UserGroupRole].pure[ConnectionIO]
        }
      }
    } yield UserOptionAndRoles(user, roles)
  }

  def createUserWithJWT(creatingUser: User, jwtUser: User.JwtFields): ConnectionIO[(User, List[UserGroupRole])] = {
    for {
      organization <- OrganizationDao.query.filter(jwtUser.organizationId).selectOption
      createdUser <- {
        organization match {
          case Some(o) =>
            val newUser = User.Create(
              jwtUser.id, Viewer, jwtUser.email,
              jwtUser.name, jwtUser.picture
            )
            create(newUser)
          case None =>
            throw new RuntimeException(
              s"Tried to create a user using a non-existent organization ID: ${jwtUser.organizationId}"
            )
        }
      }
      platformRole <- UserGroupRoleDao.create(
        UserGroupRole.Create(
          createdUser.id,
          GroupType.Platform,
          jwtUser.platformId,
          GroupRole.Member
        ).toUserGroupRole(creatingUser)
      )
      organizationRole <- UserGroupRoleDao.create(
        UserGroupRole.Create(
          createdUser.id,
          GroupType.Organization,
          organization.getOrElse(
            throw new RuntimeException("Tried to create a user role using a non-existent organization ID")
          ).id,
          GroupRole.Member
        ).toUserGroupRole(creatingUser)
      )
    } yield (createdUser, List(platformRole, organizationRole))
  }

  /* Limited update to just modifying planet credential -- users can't change their permissions*/
  def storePlanetAccessToken(user: User, updatedUser: User): ConnectionIO[Int] = {
    val cleanUpdateUser = user.copy(planetCredential = updatedUser.planetCredential)
    updateUser(cleanUpdateUser, user.id)
  }

  def updateUser(user: User, userId: String): ConnectionIO[Int] = {

    val updateTime = new Timestamp((new java.util.Date()).getTime)
    val idFilter = fr"id = ${userId}"

    (sql"""
       UPDATE users
       SET
         modified_at = ${updateTime},
         dropbox_credential = ${user.dropboxCredential.token.getOrElse("")},
         planet_credential = ${user.planetCredential.token.getOrElse("")},
         email_notifications = ${user.emailNotifications},
         email = ${user.email},
         name = ${user.name},
         profile_image_uri = ${user.profileImageUri},
         visibility = ${user.visibility}
       """ ++ Fragments.whereAndOpt(Some(idFilter))).update.run
  }

  def storeDropboxAccessToken(userId: String, accessToken: Credential): ConnectionIO[Int] = {
    sql"""UPDATE users
          SET dropbox_credential = ${accessToken}
          WHERE id = ${userId}
    """.update.run
  }

  def create(newUser: User.Create): ConnectionIO[User] = {
    val now = new Timestamp(new java.util.Date().getTime())

    sql"""
       INSERT INTO users
          (id, role, created_at, modified_at, email_notifications,
          email, name, profile_image_uri, is_superuser, is_active, visibility)
       VALUES
          (${newUser.id}, ${UserRole.toString(newUser.role)}, ${now}, ${now}, false,
          ${newUser.email}, ${newUser.name}, ${newUser.profileImageUri}, false, true, ${UserVisibility.Private.toString}::user_visibility)
       """.update.withUniqueGeneratedKeys[User](
      "id", "role", "created_at", "modified_at", "dropbox_credential", "planet_credential", "email_notifications",
      "email", "name", "profile_image_uri", "is_superuser", "is_active", "visibility"
    )
  }

  def isSuperUserF(user: User) = fr"""
    SELECT count(id) > 0
      FROM """ ++ UserDao.tableF ++ fr"""
      WHERE
        id = ${user.id} AND
        is_superuser = true AND
        is_active = true
  """

  def isSuperUser(user: User): ConnectionIO[Boolean] =
    isSuperUserF(user).query[Boolean].option.map(_.getOrElse(false))
}
