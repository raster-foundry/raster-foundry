package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

import java.util.UUID
import java.sql.Timestamp


object OrganizationDao extends Dao[Organization] {

  val tableName = "organizations"

  val selectF = sql"""
    SELECT
      id, created_at, modified_at, name
    FROM
  """ ++ tableF

  def create(
    name: String
  ): ConnectionIO[Organization] = {
    val id = UUID.randomUUID()
    val now = new Timestamp((new java.util.Date()).getTime())
    (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, created_at, modified_at, name)
      VALUES
        ($id, $now, $now, $name)
    """).update.withUniqueGeneratedKeys[Organization](
      "id", "created_at", "modified_at", "name"
    )
  }
}

