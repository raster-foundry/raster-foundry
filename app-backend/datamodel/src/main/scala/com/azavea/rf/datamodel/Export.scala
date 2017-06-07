package com.azavea.rf.datamodel

import io.circe._
import io.circe.generic.JsonCodec
import cats.implicits._

import java.util.UUID
import java.sql.Timestamp

@JsonCodec
case class Export(
  id: UUID,
  createdAt: Timestamp,
  createdBy: String,
  modifiedAt: Timestamp,
  modifiedBy: String,
  owner: String,
  organizationId: UUID,
  projectId: Option[UUID],
  exportStatus: ExportStatus,
  exportType: ExportType,
  visibility: Visibility,
  toolRunId: Option[UUID],
  exportOptions: Json
) {
  def getExportOptions: Option[ExportOptions] = exportOptions.as[ExportOptions].toOption
}

object Export {

  def tupled = (Export.apply _).tupled

  def create = Export.apply _

  @JsonCodec
  case class Create(
    organizationId: UUID,
    projectId: Option[UUID],
    exportStatus: ExportStatus,
    exportType: ExportType,
    visibility: Visibility,
    owner: Option[String],
    toolRunId: Option[UUID],
    exportOptions: Json
  ) extends OwnerCheck {

    def toExport(user: User): Export = {
      val id = UUID.randomUUID()
      val now = new Timestamp((new java.util.Date()).getTime())

      val ownerId = checkOwner(user, this.owner)

      Export(
        id = id,
        createdAt = now,
        createdBy = user.id,
        modifiedAt = now,
        modifiedBy = user.id,
        owner = ownerId,
        projectId = this.projectId,
        organizationId = this.organizationId,
        exportStatus = this.exportStatus,
        exportType = this.exportType,
        visibility = this.visibility,
        toolRunId = this.toolRunId,
        exportOptions = this.exportOptions
      )
    }
  }
}
