package com.azavea.rf.datamodel

import spray.json._
import spray.json.DefaultJsonProtocol._
import java.sql.Timestamp
import java.util.UUID

import geotrellis.vector.io.json.GeoJsonSupport
import geotrellis.vector.Geometry
import geotrellis.slick.Projected

import slick.collection.heterogeneous.{HList, HNil}
import slick.collection.heterogeneous.syntax._

case class SceneFilterFields(
  cloudCover: Option[Float] = None,
  acquisitionDate: Option[java.sql.Timestamp] = None,
  sunAzimuth: Option[Float] = None,
  sunElevation: Option[Float] = None
)

object SceneFilterFields {
  implicit val defaultSceneFilterFieldsJsonFormat = jsonFormat4(SceneFilterFields.apply)

  def tupled = (SceneFilterFields.apply _).tupled

  type TupleType = (
    Option[Float],
    Option[java.sql.Timestamp],
    Option[Float],
    Option[Float]
  )
}

case class SceneStatusFields(
  thumbnailStatus: JobStatus,
  boundaryStatus: JobStatus,
  ingestStatus: IngestStatus
)

object SceneStatusFields {
  implicit val defaultSceneStatusFieldsJsonFormat = jsonFormat3(SceneStatusFields.apply)

  def tupled = (SceneStatusFields.apply _).tupled

  type TupleType = (
    JobStatus,
    JobStatus,
    IngestStatus
  )
}

case class Scene(
  id: UUID,
  createdAt: java.sql.Timestamp,
  createdBy: String,
  modifiedAt: java.sql.Timestamp,
  modifiedBy: String,
  organizationId: UUID,
  ingestSizeBytes: Int,
  visibility: Visibility,
  tags: List[String],
  datasource: UUID,
  sceneMetadata: Map[String, Any],
  name: String,
  tileFootprint: Option[Projected[Geometry]] = None,
  dataFootprint: Option[Projected[Geometry]] = None,
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
    images,
    thumbnails,
    this.ingestLocation,
    this.filterFields,
    this.statusFields
  )
}


object Scene extends GeoJsonSupport {

  implicit val defaultSceneFormat = jsonFormat18(Scene.apply)

  /** Case class extracted from a POST request */
  case class Create(
    id: Option[UUID],
    organizationId: UUID,
    ingestSizeBytes: Int,
    visibility: Visibility,
    tags: List[String],
    datasource: UUID,
    sceneMetadata: Map[String, Any],
    name: String,
    tileFootprint: Option[Projected[Geometry]],
    dataFootprint: Option[Projected[Geometry]],
    metadataFiles: List[String],
    images: List[Image.Banded],
    thumbnails: List[Thumbnail.Identified],
    ingestLocation: Option[String],
    filterFields: SceneFilterFields = new SceneFilterFields(),
    statusFields: SceneStatusFields
  ) {
    def toScene(userId: String): Scene = {
      val now = new Timestamp((new java.util.Date()).getTime())
      Scene(
        id.getOrElse(UUID.randomUUID),
        now, // createdAt
        userId, // createdBy
        now, // modifiedAt
        userId, // modifiedBy
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

  object Create {
    implicit val defaultThumbnailWithRelatedFormat = jsonFormat16(Create.apply)
  }

  case class WithRelated(
    id: UUID,
    createdAt: Timestamp,
    createdBy: String,
    modifiedAt: Timestamp,
    modifiedBy: String,
    organizationId: UUID,
    ingestSizeBytes: Int,
    visibility: Visibility,
    tags: List[String],
    datasource: UUID,
    sceneMetadata: Map[String, Any],
    name: String,
    tileFootprint: Option[Projected[Geometry]],
    dataFootprint: Option[Projected[Geometry]],
    metadataFiles: List[String],
    images: Seq[Image.WithRelated],
    thumbnails: Seq[Thumbnail],
    ingestLocation: Option[String],
    filterFields: SceneFilterFields = new SceneFilterFields(),
    statusFields: SceneStatusFields
  )

  object WithRelated {
    implicit val defaultSceneWithRelatedFormat = ScenesJsonProtocol.SceneWithRelatedFormat

    /** Helper function to create Iterable[Scene.WithRelated] from join
      *
      * It is necessary to map over the distinct scenes because that is the only way to
      * ensure that the sort order of the query result remains ordered after grouping
      *
      * @param records result of join query to return scene with related
      * information
      */
    def fromRecords(records: Seq[(Scene, Option[Image], Option[Band], Option[Thumbnail])])
      : Iterable[Scene.WithRelated] = {
      val distinctScenes = records.map(_._1).distinct
      val groupedScenes = records.groupBy(_._1)
      val groupedBands = records.flatMap(_._3).distinct.groupBy(_.image)

      distinctScenes.map { scene =>
        val (seqImages, seqThumbnails) = groupedScenes(scene).map {
          case (_, image, _, thumbnail) => (image, thumbnail)
        }.unzip
        val imagesWithComponents: Seq[Image.WithRelated] = seqImages.flatten.distinct.map {
          image => image.withRelatedFromComponents(groupedBands.getOrElse(image.id, Seq[Band]()))
        }
        scene.withRelatedFromComponents(imagesWithComponents, seqThumbnails.flatten.distinct)
      }
    }
  }
}
