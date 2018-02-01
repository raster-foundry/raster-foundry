package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.User

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

import java.util.UUID


object UserDao extends Dao[User]("users") {

  val selectF = sql"""
    SELECT
      id, organization_id, role, created_at, modified_at,
      dropbox_credential, planet_credential, email_notifications
    FROM
  """ ++ tableF

  def select(id: String) =
    (selectF ++ fr"WHERE id = $id").query[User].unique

}

