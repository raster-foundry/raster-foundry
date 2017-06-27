package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class Image(
  id: UUID,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  organizationId: UUID,
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
  metadataFiles: List[String]
) {
  def withRelatedFromComponents(bands: Seq[Band]): Image.WithRelated = Image.WithRelated(
    this.id,
    this.createdAt,
    this.modifiedAt,
    this.organizationId,
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
  case class Create(
    organizationId: UUID,
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
        organizationId,
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
  case class Banded(
    organizationId: UUID,
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
      Image.Create(
        organizationId,
        rawDataBytes,
        visibility,
        filename,
        sourceUri,
        scene,
        imageMetadata,
        owner,
        resolutionMeters,
        metadataFiles
      ).toImage(user)
    }
  }

  @JsonCodec
  case class WithRelated(
    id: UUID,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    organizationId: UUID,
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
    bands: Seq[Band]
  ) {
    /** Helper method to extract the image component only for post requests */
    def toImage: Image = Image(
      id,
      createdAt,
      modifiedAt,
      organizationId,
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
  }

  object WithRelated {
    /** Helper function to create Iterable[Image.WithRelated] from join
      *
      * @param records result of join query to return image with related information
      */
    def fromRecords(records: Seq[(Image, Band)]): Iterable[Image.WithRelated] = {
      val distinctImages = records.map(_._1)
      val bands = records.map(_._2)
      distinctImages map { image =>
        image.withRelatedFromComponents(bands.filter(_.image == image.id))
      }
    }
  }
}
