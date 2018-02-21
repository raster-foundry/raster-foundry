package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
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
      id, created_at, modified_at, name
    FROM
  """ ++ tableF

  def create(
    name: String
  ): ConnectionIO[Organization] = {
    val id = UUID.randomUUID()
    val now = new Timestamp((new java.util.Date()).getTime())
    val newOrg = Organization.Create(name)
    createOrganization(newOrg)
  }

  def createOrganization(newOrg: Organization.Create): ConnectionIO[Organization] = {
    val id = UUID.randomUUID()
    val now = new Timestamp((new java.util.Date()).getTime())

    (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, created_at, modified_at, name)
      VALUES
        (${id}, ${now}, ${now}, ${newOrg.name})
    """).update.withUniqueGeneratedKeys[Organization](
      "id", "created_at", "modified_at", "name"
    )
  }

  def updateOrganization(org: Organization, id: UUID): ConnectionIO[Int] = {
    val updateTime = new Timestamp((new java.util.Date()).getTime)
    val idFilter = fr"id = ${id}"

    (sql"""
       UPDATE map_tokens
       SET
         modified_at = ${updateTime},
         name = ${org.name}
       WHERE id = ${id}
     """).update.run
  }

}

