package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import com.azavea.rf.bridge._
import geotrellis.vector.MultiPolygon
import geotrellis.slick.Projected

import io.circe._
import io.circe.generic.JsonCodec
import io.circe._
import io.circe.syntax._
import io.circe.parser._


@JsonCodec
case class SceneFilterFields(
  cloudCover: Option[Float] = None,
  acquisitionDate: Option[java.sql.Timestamp] = None,
  sunAzimuth: Option[Float] = None,
  sunElevation: Option[Float] = None
)

object SceneFilterFields {
  def tupled = (SceneFilterFields.apply _).tupled

  type TupleType = (
    Option[Float],
    Option[java.sql.Timestamp],
    Option[Float],
    Option[Float]
  )
}

@JsonCodec
case class SceneStatusFields(
  thumbnailStatus: JobStatus,
  boundaryStatus: JobStatus,
  ingestStatus: IngestStatus
)

object SceneStatusFields {
  def tupled = (SceneStatusFields.apply _).tupled

  type TupleType = (
    JobStatus,
    JobStatus,
    IngestStatus
  )
}

@JsonCodec
case class Scene(
  id: UUID,
  createdAt: java.sql.Timestamp,
  createdBy: String,
  modifiedAt: java.sql.Timestamp,
  modifiedBy: String,
  owner: String,
  organizationId: UUID,
  ingestSizeBytes: Int,
  visibility: Visibility,
  tags: List[String],
  datasource: UUID,
  sceneMetadata: Json,
  name: String,
  tileFootprint: Option[Projected[MultiPolygon]] = None,
  dataFootprint: Option[Projected[MultiPolygon]] = None,
  metadataFiles: List[String],
  ingestLocation: Option[String] = None,
  filterFields: SceneFilterFields = new SceneFilterFields(),
  statusFields: SceneStatusFields
) {
  def toScene = this

  def withRelatedFromComponents(
    images: Seq[Image.WithRelated],
    thumbnails: Seq[Thumbnail]
  ): Scene.WithRelated = Scene.WithRelated(
    this.id,
    this.createdAt,
    this.createdBy,
    this.modifiedAt,
    this.modifiedBy,
    this.owner,
    this.organizationId,
    this.ingestSizeBytes,
    this.visibility,
    this.tags,
    this.datasource,
    this.sceneMetadata,
    this.name,
    this.tileFootprint,
    this.dataFootprint,
    this.metadataFiles,
    images.toList,
    thumbnails.toList,
    this.ingestLocation,
    this.filterFields,
    this.statusFields
  )
}


object Scene {

  /** Case class extracted from a POST request */
  @JsonCodec
  case class Create(
                     id: Option[UUID],
                     organizationId: UUID,
                     ingestSizeBytes: Int,
                     visibility: Visibility,
                     tags: List[String],
                     datasource: UUID,
                     sceneMetadata: Json,
                     name: String,
                     owner: Option[String],
                     tileFootprint: Option[Projected[MultiPolygon]],
                     dataFootprint: Option[Projected[MultiPolygon]],
                     metadataFiles: List[String],
                     images: List[Image.Banded],
                     thumbnails: List[Thumbnail.Identified],
                     ingestLocation: Option[String],
                     filterFields: SceneFilterFields = new SceneFilterFields(),
                     statusFields: SceneStatusFields
                   ) extends OwnerCheck {
    def toScene(user: User): Scene = {
      val now = new Timestamp((new java.util.Date()).getTime())

      val ownerId = checkOwner(user, this.owner)

      Scene(
        id.getOrElse(UUID.randomUUID),
        now, // createdAt
        user.id, // createdBy
        now, // modifiedAt
        user.id, // modifiedBy
        ownerId, // owner
        organizationId,
        ingestSizeBytes,
        visibility,
        tags,
        datasource,
        sceneMetadata,
        name,
        tileFootprint,
        dataFootprint,
        metadataFiles,
        ingestLocation,
        filterFields,
        statusFields
      )
    }
  }

  @JsonCodec
  case class WithRelated(
     id: UUID,
     createdAt: Timestamp,
     createdBy: String,
     modifiedAt: Timestamp,
     modifiedBy: String,
     owner: String,
     organizationId: UUID,
     ingestSizeBytes: Int,
     visibility: Visibility,
     tags: List[String],
     datasource: UUID,
     sceneMetadata: Json,
     name: String,
     tileFootprint: Option[Projected[MultiPolygon]],
     dataFootprint: Option[Projected[MultiPolygon]],
     metadataFiles: List[String],
     images: List[Image.WithRelated],
     thumbnails: List[Thumbnail],
     ingestLocation: Option[String],
     filterFields: SceneFilterFields = new SceneFilterFields(),
     statusFields: SceneStatusFields
  ) {
    def toScene: Scene =
      Scene(
        id,
        createdAt,
        createdBy,
        modifiedAt,
        modifiedBy,
        owner,
        organizationId,
        ingestSizeBytes,
        visibility,
        tags,
        datasource,
        sceneMetadata,
        name,
        tileFootprint,
        dataFootprint,
        metadataFiles,
        ingestLocation,
        filterFields,
        statusFields
      )
  }

  object WithRelated {
    def fromRecords(records: Seq[(Scene, Option[Image], Option[Band], Option[Thumbnail])]): Iterable[Scene.WithRelated] = ???
  }
}
