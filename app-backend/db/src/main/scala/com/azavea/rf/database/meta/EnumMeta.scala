package com.azavea.rf.database.meta

import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.util.invariant.InvalidObjectMapping
import cats._, cats.data._, cats.effect.IO

trait EnumMeta {
  implicit val annotationQualityMeta: Meta[AnnotationQuality] =
    pgEnumString("annotation_quality", AnnotationQuality.fromString, _.repr)

  implicit val visibilityMeta: Meta[Visibility] =
    pgEnumString("visibility", Visibility.fromString, _.repr)

  implicit val jobStatusMeta: Meta[JobStatus] =
    pgEnumString("job_status", JobStatus.fromString, _.repr)

  implicit val ingestStatusMeta: Meta[IngestStatus] =
    pgEnumString("ingest_status", IngestStatus.fromString, _.repr)

  implicit val exportStatusMeta: Meta[ExportStatus] =
    pgEnumString("export_status", ExportStatus.fromString, _.repr)

  implicit val exportTypeMeta: Meta[ExportType] =
    pgEnumString("export_type", ExportType.fromString, _.repr)

  implicit val fileTypeMeta: Meta[FileType] =
    pgEnumString("file_type", FileType.fromString, _.repr)

  implicit val sceneTypeMeta: Meta[SceneType] =
    pgEnumString("scene_type", SceneType.fromString, _.repr)

  implicit val thumbnailSizeMeta: Meta[ThumbnailSize] =
    pgEnumString("thumbnailsize", ThumbnailSize.fromString, _.repr)

  implicit val uploadStatusMeta: Meta[UploadStatus] =
    pgEnumString("upload_status", UploadStatus.fromString, _.repr)

  implicit val uploadTypeMeta: Meta[UploadType] =
    pgEnumString("upload_type", UploadType.fromString, _.repr)

  implicit val userRoleMeta: Meta[UserRole] =
    pgEnumString("user_role", UserRole.fromString, _.repr)

  implicit val groupRoleMeta: Meta[GroupRole] =
    pgEnumString("group_role", GroupRole.fromString, _.repr)

  implicit val groupTypeMeta: Meta[GroupType] =
    pgEnumString("group_type", GroupType.fromString, _.repr)

  implicit val subjectTypeMeta: Meta[SubjectType] =
    pgEnumString("subject_type", SubjectType.fromString, _.repr)

  implicit val objectTypeMeta: Meta[ObjectType] =
    pgEnumString("object_type", ObjectType.fromString, _.repr)

  implicit val actionTypeMeta: Meta[ActionType] =
    pgEnumString("action_type", ActionType.fromString, _.repr)

  implicit val userVisibilityMeta: Meta[UserVisibility] =
    pgEnumString("user_visibility", UserVisibility.fromString, _.repr)

  implicit val orgStatusMeta: Meta[OrgStatus] =
    pgEnumString("org_status", OrgStatus.fromString, _.repr)

  implicit val orgTypeMeta: Meta[OrganizationType] =
    pgEnumString("organization_type", OrganizationType.fromString, _.repr)
}
