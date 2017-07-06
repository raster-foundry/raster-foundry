package com.azavea.rf.datamodel

import io.circe._
import java.util.UUID
import java.sql.Timestamp

import io.circe.generic.JsonCodec

@JsonCodec
case class Upload(
  id: UUID,
  createdAt: Timestamp,
  createdBy: String,
  modifiedAt: Timestamp,
  modifiedBy: String,
  owner: String,
  organizationId: UUID,
  uploadStatus: UploadStatus,
  fileType: FileType,
  uploadType: UploadType,
  files: List[String],
  datasource: UUID,
  metadata: Json,
  visibility: Visibility,
  projectId: Option[UUID],
  source: Option[String]
)

object Upload {

  def tupled = (Upload.apply _).tupled

  def create = Upload.apply _

  @JsonCodec
  case class Create(
    organizationId: UUID,
    uploadStatus: UploadStatus,
    fileType: FileType,
    uploadType: UploadType,
    files: List[String],
    datasource: UUID,
    metadata: Json,
    owner: Option[String],
    visibility: Visibility,
    projectId: Option[UUID],
    source: Option[String]
  ) extends OwnerCheck {
    def toUpload(user: User): Upload = {
      val id = UUID.randomUUID()
      val now = new Timestamp((new java.util.Date()).getTime())
      val ownerId = checkOwner(user, this.owner)

      Upload(
        id,
        now, // createdAt
        user.id, // createdBy
        now, // modifiedAt
        user.id, // modifiedBy
        ownerId, // owner
        this.organizationId,
        this.uploadStatus,
        this.fileType,
        this.uploadType,
        this.files,
        this.datasource,
        this.metadata,
        this.visibility,
        this.projectId,
        this.source
      )
    }
  }
}
