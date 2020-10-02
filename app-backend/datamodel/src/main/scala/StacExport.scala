package com.rasterfoundry.datamodel

import cats.syntax.functor._
import com.azavea.stac4s._
import io.circe.generic.JsonCodec
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json, JsonObject}

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
    annotationProjectId: Option[UUID],
    campaignId: Option[UUID]
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

  sealed abstract class Create {
    def toStacExport(user: User): StacExport
  }

  implicit val encCreate: Encoder[Create] = new Encoder[Create] {
    def apply(x: Create): Json = x match {
      case ap @ AnnotationProjectExport(_, _, _, _) => ap.asJson
      case c @ CampaignExport(_, _, _, _)           => c.asJson
    }
  }

  implicit val decCreate: Decoder[Create] =
    Decoder[AnnotationProjectExport].widen or Decoder[CampaignExport].widen

  @JsonCodec
  final case class AnnotationProjectExport(
      name: String,
      license: StacExportLicense,
      taskStatuses: List[TaskStatus],
      annotationProjectId: UUID
  ) extends Create {
    def toStacExport(user: User): StacExport = {
      val id = UUID.randomUUID()
      val now = new Timestamp(new Date().getTime)

      StacExport(
        id,
        now, // createdAt
        user.id, // createdBy
        now, // modifiedAt
        user.id, // owner
        name,
        license,
        None,
        ExportStatus.NotExported,
        this.taskStatuses.map(_.toString),
        Some(annotationProjectId),
        None
      )
    }
  }

  @JsonCodec
  case class CampaignExport(
      name: String,
      license: StacExportLicense,
      taskStatuses: List[TaskStatus],
      campaignId: UUID
  ) extends Create {
    def toStacExport(user: User): StacExport = {
      val id = UUID.randomUUID()
      val now = new Timestamp(new Date().getTime)

      StacExport(
        id,
        now, // createdAt
        user.id, // createdBy
        now, // modifiedAt
        user.id, // owner
        name,
        license,
        None,
        ExportStatus.NotExported,
        this.taskStatuses.map(_.toString),
        None,
        Some(campaignId)
      )
    }
  }
}
