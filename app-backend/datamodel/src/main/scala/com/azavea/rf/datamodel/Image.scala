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
  bands: List[String],
  imageMetadata: Map[String, Any],
  resolutionMeters: Float
)

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
    bands: List[String],
    imageMetadata: Map[String, Any],
    resolutionMeters: Float
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
        bands,
        imageMetadata,
        resolutionMeters
      )
    }
  }

  object Create {
    implicit val defaultImageCreateFormat = jsonFormat9(Create.apply _)
  }

  /** Image class when posted with an ID */
  case class Identified(
    id: Option[UUID],
    rawDataBytes: Int,
    visibility: Visibility,
    filename: String,
    sourceuri: String,
    bands: List[String],
    imageMetadata: Map[String, Any],
    resolutionMeters: Float
  ) {
    def toImage(userId: String, scene: Scene): Image = {
      val now = new Timestamp((new java.util.Date()).getTime())
      Image(
        UUID.randomUUID, // primary key
        now, // createdAt
        now, // modifiedAt
        scene.organizationId,
        userId, // createdBy: String,
        userId, // modifiedBy: String,
        rawDataBytes,
        visibility,
        filename,
        sourceuri,
        scene.id,
        bands,
        imageMetadata,
        resolutionMeters
      )
    }
  }

  object Identified {
    implicit val defaultIdentifiedImageFormat = jsonFormat8(Identified.apply _)
  }
}
