package com.rasterfoundry.datamodel

import com.azavea.stac4s._
import io.circe.JsonObject
import io.circe.generic.JsonCodec
import io.circe.syntax._

import java.sql.Timestamp
import java.util.{Date, UUID}

@JsonCodec
final case class StacExport(
    id: UUID,
    createdAt: Timestamp,
    createdBy: String,
    modifiedAt: Timestamp,
    owner: String,
    name: String,
    license: StacExportLicense,
    exportLocation: Option[String],
    exportStatus: ExportStatus,
    taskStatuses: List[String],
    annotationProjectId: Option[UUID]
) {
  def createStacCollection(
      stacVersion: String,
      stacExtensions: List[String],
      id: String,
      title: Option[String],
      description: String,
      keywords: List[String],
      providers: List[StacProvider],
      extent: StacExtent,
      summaries: JsonObject,
      properties: JsonObject,
      links: List[StacLink],
      extensionFields: JsonObject = ().asJsonObject
  ): StacCollection = {
    val updatedLinks = license.url match {
      case Some(url) =>
        StacLink(
          url,
          StacLinkType.License,
          None,
          None
        ) :: links
      case _ => links
    }

    StacCollection(
      stacVersion,
      stacExtensions,
      id,
      title,
      description,
      keywords,
      license.license,
      providers,
      extent,
      summaries,
      properties,
      updatedLinks,
      extensionFields
    )
  }
}

object StacExport {

  def tupled = (StacExport.apply _).tupled

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
      taskStatuses: List[String],
      downloadUrl: Option[String],
      annotationProjectId: Option[UUID]
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
      export.taskStatuses,
      signedDownload,
      export.annotationProjectId
    )

  @JsonCodec
  final case class Create(
      name: String,
      owner: Option[String],
      license: StacExportLicense,
      taskStatuses: List[TaskStatus],
      annotationProjectId: UUID
  ) extends OwnerCheck {
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
        this.taskStatuses.map(_.toString),
        Some(annotationProjectId)
      )
    }
  }
}
