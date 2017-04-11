package com.azavea.rf.datamodel

import io.circe._
import io.circe.parser._
import io.circe.generic.JsonCodec

import java.util.UUID
import java.sql.Timestamp

@JsonCodec
case class Export(
  id: UUID,
  createdAt: Timestamp,
  createdBy: String,
  modifiedAt: Timestamp,
  modifiedBy: String,
  organizationId: UUID,
  projectId: UUID,
  exportStatus: ExportStatus,
  exportType: ExportType,
  visibility: Visibility,
  exportOptions: Json
)

object Export {

  def tupled = (Export.apply _).tupled

  def create = Export.apply _

  @JsonCodec
  case class Create(
    organizationId: UUID,
    projectId: UUID,
    exportStatus: ExportStatus,
    exportType: ExportType,
    visibility: Visibility,
    exportOptions: Json
  ) {
    def toExport(userId: String): Export = {
      val id = UUID.randomUUID()
      val now = new Timestamp((new java.util.Date()).getTime())
      Export(
        id = id,
        createdAt = now,
        createdBy = userId,
        modifiedAt = now,
        modifiedBy = userId,
        projectId = this.projectId,
        organizationId = this.organizationId,
        exportStatus = this.exportStatus,
        exportType = this.exportType,
        visibility = this.visibility,
        exportOptions = this.exportOptions
      )
    }
  }
}
