package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import cats.implicits._
import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
final case class Export(id: UUID,
                        createdAt: Timestamp,
                        createdBy: String,
                        modifiedAt: Timestamp,
                        modifiedBy: String,
                        owner: String,
                        projectId: Option[UUID],
                        exportStatus: ExportStatus,
                        exportType: ExportType,
                        visibility: Visibility,
                        toolRunId: Option[UUID],
                        exportOptions: Json) {
  def getExportOptions: Option[ExportOptions] =
    exportOptions.as[ExportOptions].toOption
}

object Export {

  def tupled = (Export.apply _).tupled

  def create = Export.apply _

  @JsonCodec
  final case class Create(projectId: Option[UUID],
                          exportStatus: ExportStatus,
                          exportType: ExportType,
                          visibility: Visibility,
                          owner: Option[String],
                          toolRunId: Option[UUID],
                          exportOptions: Json)
      extends OwnerCheck {

    def toExport(user: User): Export = {
      val id = UUID.randomUUID()
      val now = new Timestamp(new java.util.Date().getTime)

      val ownerId = checkOwner(user, this.owner)

      Export(
        id = id,
        createdAt = now,
        createdBy = user.id,
        modifiedAt = now,
        modifiedBy = user.id,
        owner = ownerId,
        projectId = this.projectId,
        exportStatus = this.exportStatus,
        exportType = this.exportType,
        visibility = this.visibility,
        toolRunId = this.toolRunId,
        exportOptions = this.exportOptions
      )
    }
  }
}
