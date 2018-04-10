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


object OrganizationDao extends Dao[Organization] {

  val tableName = "organizations"

  val selectF = sql"""
    SELECT
      id, created_at, modified_at, name, platform_id
    FROM
  """ ++ tableF

  def create(
    org: Organization
  ): ConnectionIO[Organization] = {
    (fr"INSERT INTO" ++ tableF ++ fr"""
          (id, created_at, modified_at, name, platform_id)
        VALUES
          (${org.id}, ${org.createdAt}, ${org.modifiedAt}, ${org.name}, ${org.platformId})
    """).update.withUniqueGeneratedKeys[Organization](
      "id", "created_at", "modified_at", "name", "platform_id"
    )
  }

  def update(org: Organization, id: UUID): ConnectionIO[Int] = {
    val updateTime = new Timestamp((new java.util.Date()).getTime)

    (fr"UPDATE" ++ tableF ++ fr"""SET
         modified_at = ${updateTime},
         name = ${org.name}
       WHERE id = ${id}
     """).update.run
  }

}

