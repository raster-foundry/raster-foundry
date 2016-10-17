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
  imageMetadata: Map[String, Any]
)

object Image {

  def create = Create.apply _

  def tupled = (Image.apply _).tupled

  implicit val defaultImageFormat = jsonFormat13(Image.apply _)

  case class Create(
    organizationId: UUID,
    rawDataBytes: Int,
    visibility: Visibility,
    filename: String,
    sourceUri: String,
    scene: UUID,
    bands: List[String],
    imageMetadata: Map[String, Any]
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
        imageMetadata
      )
    }
  }

  object Create {
    implicit val defaultImageCreateFormat = jsonFormat8(Create.apply _)
  }

  /** Image class when posted with an ID */
  case class Identified(
    id: Option[UUID],
    rawDataBytes: Int,
    visibility: Visibility,
    filename: String,
    sourceuri: String,
    bands: List[String],
    imageMetadata: Map[String, Any]
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
        imageMetadata
      )
    }
  }

  object Identified {
    implicit val defaultIdentifiedImageFormat = jsonFormat7(Identified.apply _)
  }
}
