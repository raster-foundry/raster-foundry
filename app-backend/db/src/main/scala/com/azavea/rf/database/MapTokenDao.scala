package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

import java.sql.Timestamp
import java.util.{Date, UUID}


object MapTokenDao extends Dao[MapToken]("map_tokens") {

  val selectF =
    sql"""
      SELECT
        id, created_at, created_by, modified_at, modified_by,
        owner, organization_id, name, project_id, toolrun_id
      FROM
    """ ++ tableF

  def select(id: UUID) =
    (selectF ++ fr"WHERE id = $id").query[MapToken].unique

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

