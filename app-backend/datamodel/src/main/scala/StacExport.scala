package com.rasterfoundry.datamodel

import geotrellis.server.stac.{
  StacCollection,
  StacExtent,
  StacLink,
  StacProvider,
  License => StacLinkLicense
}
import io.circe.JsonObject
import io.circe.generic.JsonCodec

import java.sql.Timestamp
import java.util.{Date, UUID}

@JsonCodec
final case class StacExport(id: UUID,
                            createdAt: Timestamp,
                            createdBy: String,
                            modifiedAt: Timestamp,
                            owner: String,
                            name: String,
                            license: StacExportLicense,
                            exportLocation: Option[String],
                            exportStatus: ExportStatus,
                            layerDefinitions: List[StacExport.LayerDefinition],
                            taskStatuses: List[String]) {
  def createStacCollection(stacVersion: String,
                           id: String,
                           title: Option[String],
                           description: String,
                           keywords: List[String],
                           version: String,
                           providers: List[StacProvider],
                           extent: StacExtent,
                           properties: JsonObject,
                           links: List[StacLink]): StacCollection = {
    val updatedLinks = license.url match {
      case Some(url) =>
        StacLink(
          url,
          StacLinkLicense,
          None,
          None,
          List()
        ) :: links
      case _ => links
    }

    StacCollection(
      stacVersion,
      id,
      title,
      description,
      keywords,
      version,
      license.license,
      providers,
      extent,
      properties,
      updatedLinks
    )
  }
}

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
      license: StacExportLicense,
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
      export.license,
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
                          license: StacExportLicense,
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
        license,
        None,
        ExportStatus.NotExported,
        this.layerDefinitions,
        this.taskStatuses.map(_.toString)
      )
    }
  }
}
