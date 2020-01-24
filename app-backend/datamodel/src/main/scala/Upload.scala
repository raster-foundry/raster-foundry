package com.rasterfoundry.datamodel

import io.circe._
import io.circe.generic.JsonCodec

import java.sql.Timestamp
import java.util.UUID

@JsonCodec
final case class Upload(
    id: UUID,
    createdAt: Timestamp,
    createdBy: String,
    modifiedAt: Timestamp,
    owner: String,
    uploadStatus: UploadStatus,
    fileType: FileType,
    uploadType: UploadType,
    files: List[String],
    datasource: UUID,
    metadata: Json,
    visibility: Visibility,
    projectId: Option[UUID],
    layerId: Option[UUID],
    source: Option[String],
    keepInSourceBucket: Boolean
)

object Upload {

  def tupled = (Upload.apply _).tupled

  def create = Upload.apply _

  @JsonCodec
  final case class Create(
      uploadStatus: UploadStatus,
      fileType: FileType,
      uploadType: UploadType,
      files: List[String],
      datasource: UUID,
      metadata: Json,
      owner: Option[String],
      visibility: Visibility,
      projectId: Option[UUID],
      layerId: Option[UUID],
      source: Option[String],
      keepInSourceBucket: Option[Boolean]
  ) {
    def toUpload(
        user: User,
        userPlatformAdmin: (UUID, Boolean),
        ownerPlatform: Option[UUID]
    ): Upload = {
      val id = UUID.randomUUID()
      val now = new Timestamp(new java.util.Date().getTime)
      // This logic isn't in OwnerCheck because we don't know that we want to let Platform Admins set owners on
      // everything in the universe yet. If that does become the case we can move it to the trait.
      val ownerId = (owner, ownerPlatform, user, userPlatformAdmin) match {
        // if no intended owner, the acting user is the owner
        case (intendedOwnerO, _, user, _) if user.isSuperuser =>
          intendedOwnerO.getOrElse(user.id)
        case (None, None, user, _) =>
          user.id
        // if intended owner and acting user are the same, then the other conditions shake out in the wash
        case (Some(intendedOwner), _, user, _) if user.id == intendedOwner =>
          user.id
        // when intendedOwner and user are different people, we check that both the two users' platforms
        // match and that the acting user is an admin of that platform
        case (
            Some(intendedOwner),
            Some(ownerPlatformId),
            _,
            (userPlatformId, userIsPlatformAdmin)
            ) =>
          if (ownerPlatformId == userPlatformId && userIsPlatformAdmin) {
            intendedOwner
          } else {
            throw new IllegalArgumentException(
              "Insufficient permissions to set owner on object"
            )
          }
        // Otherwise something bizarre has happened
        case (None, Some(_), _, _) | (Some(_), _, _, _) =>
          throw new IllegalArgumentException(
            "Owner and ownerPlatform must both be Some(x) or both be None"
          )
      }

      Upload(
        id,
        now, // createdAt
        user.id, // createdBy
        now, // modifiedAt
        ownerId, // owner
        this.uploadStatus,
        this.fileType,
        this.uploadType,
        this.files,
        this.datasource,
        this.metadata,
        this.visibility,
        this.projectId,
        this.layerId,
        this.source,
        this.keepInSourceBucket.getOrElse(false)
      )
    }
  }

  @JsonCodec final case class PutUrl(signedUrl: String)
}
