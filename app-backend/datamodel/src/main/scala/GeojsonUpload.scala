package com.rasterfoundry.datamodel

import io.circe.generic.JsonCodec

import java.sql.Timestamp
import java.util.UUID

@JsonCodec
final case class GeojsonUpload(
    id: UUID,
    createdAt: Timestamp,
    createdBy: String,
    modifiedAt: Timestamp,
    uploadStatus: UploadStatus,
    fileType: FileType,
    uploadType: UploadType,
    files: List[String],
    projectId: UUID,
    projectLayerId: UUID,
    annotationGroup: UUID,
    keepFiles: Boolean
)

object GeojsonUpload {
  @JsonCodec
  final case class Create(
      uploadStatus: UploadStatus,
      fileType: FileType,
      uploadType: UploadType,
      files: List[String],
      keepFiles: Boolean
  ) {
    def toGeojsonUpload(
        user: User,
        projectId: UUID,
        projectLayerId: UUID,
        annotationGroup: UUID
    ): GeojsonUpload = {
      val id = UUID.randomUUID()
      val now = new Timestamp(new java.util.Date().getTime)
      GeojsonUpload(
        id,
        now, // createdAt
        user.id, // createdBy
        now, // modifiedAt
        this.uploadStatus,
        this.fileType,
        this.uploadType,
        this.files,
        projectId,
        projectLayerId,
        annotationGroup,
        this.keepFiles
      )
    }
  }
}
