package com.azavea.rf.database

import java.sql.Timestamp

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel.{User, UserRole, Credential}
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
      id, organization_id, role, created_at, modified_at,
      dropbox_credential, planet_credential, email_notifications
    FROM
  """ ++ tableF

  def unsafeGetUserById(id: String): ConnectionIO[User] = {
    query.filter(fr"id = ${id}").select
  }

  def getUserById(id: String): ConnectionIO[Option[User]] = {
    query.filter(fr"id = ${id}").selectOption
  }

  def createUserWithAuthId(id: String): ConnectionIO[User] = {
    for {
      org <- OrganizationDao.query.filter(fr"name = 'PUBLIC'").select
      user <- {
        val newUser = User.Create(id, org.id)
        create(newUser)
      }
    } yield user
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
         organization_id = ${user.organizationId},
         modified_at = ${updateTime},
         dropbox_credential = ${user.dropboxCredential.token.getOrElse("")},
         planet_credential = ${user.planetCredential.token.getOrElse("")},
         email_notifications = ${user.emailNotifications}
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
          (id, organization_id, role, created_at, modified_at, email_notifications)
       VALUES
          (${newUser.id}, ${newUser.organizationId}, ${UserRole.toString(newUser.role)}, ${now}, ${now}, false)
       """.update.withUniqueGeneratedKeys[User](
      "id", "organization_id", "role", "created_at", "modified_at", "dropbox_credential", "planet_credential", "email_notifications"
    )
  }
}

