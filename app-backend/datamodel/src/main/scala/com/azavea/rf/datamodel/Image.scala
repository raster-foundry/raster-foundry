package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
final case class Image(id: UUID,
                       createdAt: Timestamp,
                       modifiedAt: Timestamp,
                       createdBy: String,
                       modifiedBy: String,
                       owner: String,
                       rawDataBytes: Long,
                       visibility: Visibility,
                       filename: String,
                       sourceUri: String,
                       scene: UUID,
                       imageMetadata: Json,
                       resolutionMeters: Float,
                       metadataFiles: List[String]) {
  def withRelatedFromComponents(bands: List[Band]): Image.WithRelated =
    Image.WithRelated(
      this.id,
      this.createdAt,
      this.modifiedAt,
      this.createdBy,
      this.modifiedBy,
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
  final case class Create(rawDataBytes: Long,
                          visibility: Visibility,
                          filename: String,
                          sourceUri: String,
                          scene: UUID,
                          imageMetadata: Json,
                          owner: Option[String],
                          resolutionMeters: Float,
                          metadataFiles: List[String])
      extends OwnerCheck {
    def toImage(user: User): Image = {
      val now = new Timestamp((new java.util.Date).getTime)

      val ownerId = checkOwner(user, this.owner)

      Image(
        UUID.randomUUID, // primary key
        now, // createdAt
        now, // modifiedAt
        user.id, // createdBy: String,
        user.id, // modifiedBy: String,
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
  final case class Banded(rawDataBytes: Long,
                          visibility: Visibility,
                          filename: String,
                          sourceUri: String,
                          owner: Option[String],
                          scene: UUID,
                          imageMetadata: Json,
                          resolutionMeters: Float,
                          metadataFiles: List[String],
                          bands: Seq[Band.Create]) {
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
  final case class WithRelated(id: UUID,
                               createdAt: Timestamp,
                               modifiedAt: Timestamp,
                               createdBy: String,
                               modifiedBy: String,
                               owner: String,
                               rawDataBytes: Long,
                               visibility: Visibility,
                               filename: String,
                               sourceUri: String,
                               scene: UUID,
                               imageMetadata: Json,
                               resolutionMeters: Float,
                               metadataFiles: List[String],
                               bands: List[Band]) {

    /** Helper method to extract the image component only for post requests */
    def toImage: Image = Image(
      id,
      createdAt,
      modifiedAt,
      createdBy,
      modifiedBy,
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

  object WithRelated {

    /** Helper function to create Iterable[Image.WithRelated] from join
      *
      * @param records result of join query to return image with related information
      */
    def fromRecords(
        records: List[(Image, Band)]
    ): Iterable[Image.WithRelated] = {
      val distinctImages = records.map(_._1)
      val bands = records.map(_._2)
      distinctImages map { image =>
        image.withRelatedFromComponents(bands.filter(_.image == image.id))
      }
    }
  }

  @JsonCodec
  final case class Downloadable(filename: String,
                                sourceUri: String,
                                downloadUri: String)
}
