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


object ExportDao extends Dao[Export]("exports") {

  val selectF = fr"""
    SELECT
      id, created_at, created_by, modified_at, modified_by, owner,
      organization_id, project_id, export_status, export_type,
      visibility, toolrun_id, export_options
    FROM
  """ ++ tableF

  def select(id: UUID) =
    (selectF ++ fr"WHERE id = $id").query[Export].unique

  def listFilters(params: ExportQueryParameters, user: User) =
    List(
      params.organization.map({ orgId => fr"organization = $orgId"}),
      params.project.map({ projId => fr"project_id = $projId"}),
      params.exportStatus.toList.toNel.map({ statuses =>
        val exportStatuses = statuses.map({ status =>
          try ExportStatus.fromString(status)
          catch {
            case e : Exception => throw new IllegalArgumentException(s"Invalid Ingest Status: $status")
          }
        })
        Fragments.in(fr"export_status", exportStatuses)
      })
    )

  def list(
    params: ExportQueryParameters,
    user: User,
    pageRequest: Option[PageRequest]
  ): ConnectionIO[List[Export]] = {
    val filters: List[Option[Fragment]] = listFilters(params, user)
    list(filters, pageRequest)
  }

  def page(
    params: ExportQueryParameters,
    user: User,
    pageRequest: PageRequest
  )(implicit xa: Transactor[IO]): Future[PaginatedResponse[Export]] = {
    val filters: List[Option[Fragment]] = listFilters(params, user)
    page(filters, pageRequest)
  }

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
}

