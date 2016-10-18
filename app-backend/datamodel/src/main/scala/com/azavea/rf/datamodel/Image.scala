package com.azavea.rf.datamodel

import spray.json._
import spray.json.DefaultJsonProtocol._
import geotrellis.vector.Geometry
import geotrellis.slick.Projected

import java.util.UUID
import java.sql.Timestamp
import java.net.URI

case class Image(
  id: UUID,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  organizationId: UUID,
  createdBy: String,
  modifiedBy: String,
  rawDataBytes: Int,
  visibility: Visibility,
  sourceUri: URI,
  scene: UUID,
  bands: List[Int],
  imageMetadata: Map[String, Any],
  extent: Option[Projected[Geometry]] = None
)

object Image {

  def create = Create.apply _

  def tupled = (Image.apply _).tupled

  implicit val defaultImageFormat = jsonFormat13(Image.apply _)

  case class Create(
    organizationId: UUID,
    rawDataBytes: Int,
    visibility: Visibility,
    sourceUri: URI,
    scene: UUID,
    bands: List[Int],
    imageMetadata: Map[String, Any],
    extent: Option[Projected[Geometry]] = None
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
        sourceUri,
        scene,
        bands,
        imageMetadata,
        extent
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
    sourceUri: URI,
    bands: List[Int],
    imageMetadata: Map[String, Any],
    extent: Option[Projected[Geometry]] = None
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
        sourceUri,
        scene.id,
        bands,
        imageMetadata,
        extent
      )
    }
  }

  object Identified {
    implicit val defaultIdentifiedImageFormat = jsonFormat7(Identified.apply _)
  }
}
