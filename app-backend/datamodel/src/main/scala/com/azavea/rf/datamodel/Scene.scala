package com.azavea.rf.datamodel

import spray.json._
import spray.json.DefaultJsonProtocol._
import java.sql.Timestamp
import java.util.UUID

import geotrellis.vector.io.json.GeoJsonSupport
import geotrellis.vector.Geometry
import geotrellis.slick.Projected


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
  cloudCover: Option[Float] = None,
  acquisitionDate: Option[java.sql.Timestamp] = None,
  thumbnailStatus: JobStatus,
  boundaryStatus: JobStatus,
  sunAzimuth: Option[Float] = None,
  sunElevation: Option[Float] = None,
  name: String,
  tileFootprint: Option[Projected[Geometry]] = None,
  dataFootprint: Option[Projected[Geometry]] = None,
  metadataFiles: List[String],
  ingestLocation: Option[String] = None
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
    this.cloudCover,
    this.acquisitionDate,
    this.thumbnailStatus,
    this.boundaryStatus,
    this.sunAzimuth,
    this.sunElevation,
    this.name,
    this.tileFootprint,
    this.dataFootprint,
    this.metadataFiles,
    images,
    thumbnails,
    this.ingestLocation
  )
}


object Scene extends GeoJsonSupport {

  def tupled = (Scene.apply _).tupled

  def create = Create.apply _

  implicit val defaultSceneFormat = jsonFormat22(Scene.apply)


  /** Case class extracted from a POST request */
  case class Create(
    id: Option[UUID],
    organizationId: UUID,
    ingestSizeBytes: Int,
    visibility: Visibility,
    tags: List[String],
    datasource: UUID,
    sceneMetadata: Map[String, Any],
    cloudCover: Option[Float],
    acquisitionDate: Option[java.sql.Timestamp],
    thumbnailStatus: JobStatus,
    boundaryStatus: JobStatus,
    sunAzimuth: Option[Float],
    sunElevation: Option[Float],
    name: String,
    tileFootprint: Option[Projected[Geometry]],
    dataFootprint: Option[Projected[Geometry]],
    metadataFiles: List[String],
    images: List[Image.Banded],
    thumbnails: List[Thumbnail.Identified],
    ingestLocation: Option[String]
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
        cloudCover,
        acquisitionDate,
        thumbnailStatus,
        boundaryStatus,
        sunAzimuth,
        sunElevation,
        name,
        tileFootprint,
        dataFootprint,
        metadataFiles,
        ingestLocation
      )
    }
  }

  object Create {
    implicit val defaultThumbnailWithRelatedFormat = jsonFormat20(Create.apply)
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
    cloudCover: Option[Float],
    acquisitionDate: Option[java.sql.Timestamp],
    thumbnailStatus: JobStatus,
    boundaryStatus: JobStatus,
    sunAzimuth: Option[Float],
    sunElevation: Option[Float],
    name: String,
    tileFootprint: Option[Projected[Geometry]],
    dataFootprint: Option[Projected[Geometry]],
    metadataFiles: List[String],
    images: Seq[Image.WithRelated],
    thumbnails: Seq[Thumbnail],
    ingestLocation: Option[String]
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
