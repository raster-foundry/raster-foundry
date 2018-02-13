package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.User
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

  def getUserById(id: String): Future[Option[User]] = ???

  def createUserWithAuthId(id: String): Future[User] = ???
}

