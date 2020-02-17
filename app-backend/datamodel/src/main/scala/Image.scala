package com.rasterfoundry.datamodel

import io.circe._
import io.circe.generic.JsonCodec

import java.sql.Timestamp
import java.util.UUID

@JsonCodec
final case class Image(
    id: UUID,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    createdBy: String,
    owner: String,
    rawDataBytes: Long,
    visibility: Visibility,
    filename: String,
    sourceUri: String,
    scene: UUID,
    imageMetadata: Json,
    resolutionMeters: Float,
    metadataFiles: List[String]
) {
  def withRelatedFromComponents(bands: List[Band]): Image.WithRelated =
    Image.WithRelated(
      this.id,
      this.createdAt,
      this.modifiedAt,
      this.createdBy,
      this.owner,
      this.rawDataBytes,
      this.visibility,
      this.filename,
      this.sourceUri,
      this.scene,
      this.imageMetadata,
      this.resolutionMeters,
      this.metadataFiles,
      bands
    )
}

object Image {

  def create = Create.apply _

  def tupled = (Image.apply _).tupled

  @JsonCodec
  final case class Create(
      rawDataBytes: Long,
      visibility: Visibility,
      filename: String,
      sourceUri: String,
      scene: UUID,
      imageMetadata: Json,
      owner: Option[String],
      resolutionMeters: Float,
      metadataFiles: List[String]
  ) extends OwnerCheck {
    def toImage(user: User): Image = {
      val now = new Timestamp((new java.util.Date).getTime)

      val ownerId = checkOwner(user, this.owner)

      Image(
        UUID.randomUUID, // primary key
        now, // createdAt
        now, // modifiedAt
        user.id, // createdBy: String,
        ownerId, // owner: String
        rawDataBytes,
        visibility,
        filename,
        sourceUri,
        scene,
        imageMetadata,
        resolutionMeters,
        metadataFiles
      )
    }
  }

  /** Image class when posted with bands */
  @JsonCodec
  final case class Banded(
      rawDataBytes: Long,
      visibility: Visibility,
      filename: String,
      sourceUri: String,
      owner: Option[String],
      scene: UUID,
      imageMetadata: Json,
      resolutionMeters: Float,
      metadataFiles: List[String],
      bands: Seq[Band.Create]
  ) {
    def toImage(user: User): Image = {
      Image
        .Create(
          rawDataBytes,
          visibility,
          filename,
          sourceUri,
          scene,
          imageMetadata,
          owner,
          resolutionMeters,
          metadataFiles
        )
        .toImage(user)
    }
  }

  @JsonCodec
  final case class WithRelated(
      id: UUID,
      createdAt: Timestamp,
      modifiedAt: Timestamp,
      createdBy: String,
      owner: String,
      rawDataBytes: Long,
      visibility: Visibility,
      filename: String,
      sourceUri: String,
      scene: UUID,
      imageMetadata: Json,
      resolutionMeters: Float,
      metadataFiles: List[String],
      bands: List[Band]
  ) {

    /** Helper method to extract the image component only for post requests */
    def toImage: Image = Image(
      id,
      createdAt,
      modifiedAt,
      createdBy,
      owner,
      rawDataBytes,
      visibility,
      filename,
      sourceUri,
      scene,
      imageMetadata,
      resolutionMeters,
      metadataFiles
    )

    def toDownloadable(downloadUri: String): Image.Downloadable =
      Downloadable(
        this.filename,
        this.sourceUri,
        downloadUri
      )
  }

  @JsonCodec
  final case class Downloadable(
      filename: String,
      sourceUri: String,
      downloadUri: String
  )
}
