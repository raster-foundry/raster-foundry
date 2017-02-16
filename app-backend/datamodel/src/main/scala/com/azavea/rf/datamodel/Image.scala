package com.azavea.rf.datamodel

import spray.json._
import spray.json.DefaultJsonProtocol._
import java.util.UUID
import java.sql.Timestamp

case class Image(
  id: UUID,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  organizationId: UUID,
  createdBy: String,
  modifiedBy: String,
  rawDataBytes: Int,
  visibility: Visibility,
  filename: String,
  sourceUri: String,
  scene: UUID,
  imageMetadata: Map[String, Any],
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

  implicit val defaultImageFormat = jsonFormat14(Image.apply _)

  case class Create(
    organizationId: UUID,
    rawDataBytes: Int,
    visibility: Visibility,
    filename: String,
    sourceUri: String,
    scene: UUID,
    imageMetadata: Map[String, Any],
    resolutionMeters: Float,
    metadataFiles: List[String]
  ) {
    def toImage(userId: String): Image = {
      val now = new Timestamp((new java.util.Date).getTime)

      Image(
        UUID.randomUUID, // primary key
        now, // createdAt
        now, // modifiedAt
        organizationId,
        userId, // createdBy: String,
        userId, // modifiedBy: String,
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

  object Create {
    implicit val defaultImageCreateFormat = jsonFormat9(Create.apply _)
  }

  /** Image class when posted with bands */
  case class Banded(
    organizationId: UUID,
    rawDataBytes: Int,
    visibility: Visibility,
    filename: String,
    sourceUri: String,
    scene: UUID,
    imageMetadata: Map[String, Any],
    resolutionMeters: Float,
    metadataFiles: List[String],
    bands: Seq[Band.Create]
  ) {
    def toImage(userId: String): Image = {
      Image.Create(
        organizationId,
        rawDataBytes,
        visibility,
        filename,
        sourceUri,
        scene,
        imageMetadata,
        resolutionMeters,
        metadataFiles
      ).toImage(userId)
    }
  }

  object Banded {
    implicit val defaultImageBandedFormat = jsonFormat10(Banded.apply _)
  }

  case class WithRelated(
    id: UUID,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    organizationId: UUID,
    createdBy: String,
    modifiedBy: String,
    rawDataBytes: Int,
    visibility: Visibility,
    filename: String,
    sourceUri: String,
    scene: UUID,
    imageMetadata: Map[String, Any],
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
    implicit val defaultImageWithRelatedFormat = jsonFormat15(WithRelated.apply _)

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
