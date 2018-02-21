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
import java.sql.Timestamp
import java.util.{Date, UUID}

import scala.concurrent.Future


object MapTokenDao extends Dao[MapToken] {

  val tableName = "map_tokens"

  val selectF =
    sql"""
      SELECT
        id, created_at, created_by, modified_at, modified_by,
        owner, organization_id, name, project_id, toolrun_id
      FROM
    """ ++ tableF

  def insertMapToken(newMapToken: MapToken.Create, user: User): ConnectionIO[MapToken] = ???

  def updateMapToken(mapToken: MapToken, id: UUID, user: User): ConnectionIO[Int] = ???

  def deleteMapToken(id: UUID, user: User): ConnectionIO[Int] = ???

  def create(
    user: User,
    owner: Option[String],
    organizationId: UUID,
    name: String,
    project: Option[UUID],
    toolRun: Option[UUID]
  ): ConnectionIO[MapToken] = {
    val id = UUID.randomUUID
    val now = new Timestamp((new java.util.Date()).getTime())
    val ownerId = util.Ownership.checkOwner(user, owner)
    (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, created_at, created_by, modified_at, modified_by,
        owner, organization_id, name, project_id, toolrun_id)
      VALUES
        ($id, $now, ${user.id}, $now, ${user.id},
        $ownerId, $organizationId, $name, $project, $toolRun)
    """).update.withUniqueGeneratedKeys[MapToken](
      "id", "created_at", "created_by", "modified_at", "modified_by",
      "owner", "organization_id", "name", "project_id", "toolrun_id"
    )
  }
}

