package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.database.util._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import io.circe._
import com.lonelyplanet.akka.http.extensions.PageRequest

import scala.concurrent.Future
import java.sql.Timestamp
import java.util.{Date, UUID}


object ExportDao extends Dao[Export] {

  val tableName = "exports"

  val selectF = fr"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, owner,
      organization_id, project_id, export_status, export_type,
      visibility, toolrun_id, export_options
    FROM
  """ ++ tableF

  def create(
    user: User,
    organizationId: UUID,
    projectId: Option[UUID],
    exportStatus: ExportStatus,
    exportType: ExportType,
    visibility: Visibility,
    owner: Option[String],
    toolRunId: Option[UUID],
    exportOptions: Json
  ): ConnectionIO[Export] = {
    val id = UUID.randomUUID
    val now = new Timestamp((new java.util.Date()).getTime())
    val ownerId = util.Ownership.checkOwner(user, owner)
    (fr"INSERT INTO" ++ tableF ++ fr"""
        (id, created_at, created_by, modified_at, modified_by, owner,
        organization_id, project_id, export_status, export_type,
        visibility, toolrun_id, export_options)
      VALUES
        ($id, $now, ${user.id}, $now, ${user.id}, $ownerId,
        $organizationId, $projectId, $exportStatus, $exportType,
        $visibility, $toolRunId, $exportOptions)
    """).update.withUniqueGeneratedKeys[Export](
        "id", "created_at", "created_by", "modified_at", "modified_by", "owner",
        "organization_id", "project_id", "export_status", "export_type",
        "visibility", "toolrun_id", "export_options"
    )
  }

  def insert(export: Export, user: User): ConnectionIO[Export] = ???

  def update(export: Export, id: UUID, user: User): ConnectionIO[Int] = ???

  def getWithStatus(id: UUID, user: User, status: ExportStatus): ConnectionIO[Export] = ???

  def getExportDefinition(id: UUID, user: User): ConnectionIO[ExportDefinition] = ???
}

