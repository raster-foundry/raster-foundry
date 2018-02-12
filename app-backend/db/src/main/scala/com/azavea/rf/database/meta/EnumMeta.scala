package com.azavea.rf.database.meta

import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.util.invariant.InvalidObjectMapping
import cats._, cats.data._, cats.effect.IO


trait EnumMeta {
  implicit val annotationQualityMeta: Meta[AnnotationQuality] =
    pgEnumString("AnnotationQuality", AnnotationQuality.fromString, _.repr)

  implicit val visibilityMeta: Meta[Visibility] =
    pgEnumString("Visibility", Visibility.fromString, _.repr)

  implicit val jobStatusMeta: Meta[JobStatus] =
    pgEnumString("JobStatus", JobStatus.fromString, _.repr)

  implicit val ingestStatusMeta: Meta[IngestStatus] =
    pgEnumString("IngestStatus", IngestStatus.fromString, _.repr)

  implicit val exportStatusMeta: Meta[ExportStatus] =
    pgEnumString("export_status", ExportStatus.fromString, _.repr)

  implicit val exportTypeMeta: Meta[ExportType] =
    pgEnumString("export_type", ExportType.fromString, _.repr)

  implicit val fileTypeMeta: Meta[FileType] =
    pgEnumString("FileType", FileType.fromString, _.repr)

  implicit val thumbnailSizeMeta: Meta[ThumbnailSize] =
    pgEnumString("ThumbnailSize", ThumbnailSize.fromString, _.repr)

  implicit val uploadStatusMeta: Meta[UploadStatus] =
    pgEnumString("UploadStatus", UploadStatus.fromString, _.repr)

  implicit val uploadTypeMeta: Meta[UploadType] =
    pgEnumString("UploadType", UploadType.fromString, _.repr)

  implicit val userRoleMeta: Meta[UserRole] =
    pgEnumString("UserRole", UserRole.fromString, _.repr)
}

