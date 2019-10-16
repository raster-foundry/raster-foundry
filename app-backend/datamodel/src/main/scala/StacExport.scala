package com.rasterfoundry.datamodel

import java.sql.Timestamp
import java.util.{UUID, Date}

import io.circe.generic.JsonCodec

@JsonCodec
final case class StacExport(id: UUID,
                            createdAt: Timestamp,
                            createdBy: String,
                            modifiedAt: Timestamp,
                            owner: String,
                            name: String,
                            exportLocation: Option[String],
                            exportStatus: ExportStatus,
                            layerDefinitions: List[StacExport.LayerDefinition],
                            taskStatuses: List[String])

object StacExport {

  def tupled = (StacExport.apply _).tupled

  @JsonCodec
  final case class LayerDefinition(projectId: UUID, layerId: UUID)

  @JsonCodec
  final case class WithSignedDownload(
      id: UUID,
      createdAt: Timestamp,
      createdBy: String,
      modifiedAt: Timestamp,
      owner: String,
      name: String,
      exportLocation: Option[String],
      exportStatus: ExportStatus,
      layerDefinitions: List[StacExport.LayerDefinition],
      taskStatuses: List[String],
      downloadUrl: Option[String]
  )

  def signDownloadUrl(export: StacExport, signedDownload: Option[String]) =
    WithSignedDownload(
      export.id,
      export.createdAt,
      export.createdBy,
      export.modifiedAt,
      export.owner,
      export.name,
      export.exportLocation,
      export.exportStatus,
      export.layerDefinitions,
      export.taskStatuses,
      signedDownload
    )

  @JsonCodec
  final case class Create(name: String,
                          owner: Option[String],
                          layerDefinitions: List[StacExport.LayerDefinition],
                          taskStatuses: List[TaskStatus])
      extends OwnerCheck {
    def toStacExport(user: User): StacExport = {
      val id = UUID.randomUUID()
      val now = new Timestamp(new Date().getTime)
      val ownerId = checkOwner(user, this.owner)

      StacExport(
        id,
        now, // createdAt
        user.id, // createdBy
        now, // modifiedAt
        ownerId, // owner
        this.name,
        None,
        ExportStatus.NotExported,
        this.layerDefinitions,
        this.taskStatuses.map(_.toString)
      )
    }
  }
}
