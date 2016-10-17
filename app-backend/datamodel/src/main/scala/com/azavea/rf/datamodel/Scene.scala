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
  resolutionMeters: Float,
  tags: List[String],
  datasource: String,
  sceneMetadata: Map[String, Any],
  cloudCover: Option[Float] = None,
  acquisitionDate: Option[java.sql.Timestamp] = None,
  thumbnailStatus: JobStatus,
  boundaryStatus: JobStatus,
  status: JobStatus,
  sunAzimuth: Option[Float] = None,
  sunElevation: Option[Float] = None,
  name: String,
  footprint: Option[Projected[Geometry]] = None
) {
  def toScene = this

  def withRelatedFromComponents(
    images: Seq[Image],
    thumbnails: Seq[Thumbnail]
  ): Scene.WithRelated = Scene.withRelated(
    this.id,
    this.createdAt,
    this.createdBy,
    this.modifiedAt,
    this.modifiedBy,
    this.organizationId,
    this.visibility,
    this.resolutionMeters,
    this.tags,
    this.datasource,
    this.sceneMetadata,
    this.cloudCover,
    this.acquisitionDate,
    this.thumbnailStatus,
    this.boundaryStatus,
    this.status,
    this.sunAzimuth,
    this.sunElevation,
    this.name,
    images,
    this.footprint,
    thumbnails
  )
}


object Scene extends GeoJsonSupport {

  def tupled = (Scene.apply _).tupled

  def create = Create.apply _

  def withRelated = WithRelated.apply _

  implicit val defaultThumbnailFormat = jsonFormat21(Scene.apply)

  /** Case class extracted from a POST request */
  case class Create(
    organizationId: UUID,
    ingestSizeBytes: Int,
    visibility: Visibility,
    resolutionMeters: Float,
    tags: List[String],
    datasource: String,
    sceneMetadata: Map[String, Any],
    cloudCover: Option[Float],
    acquisitionDate: Option[java.sql.Timestamp],
    thumbnailStatus: JobStatus,
    boundaryStatus: JobStatus,
    status: JobStatus,
    sunAzimuth: Option[Float],
    sunElevation: Option[Float],
    name: String,
    images: List[Image.Identified],
    footprint: Option[Projected[Geometry]],
    thumbnails: List[Thumbnail.Identified]
  ) {
    def toScene(userId: String): Scene = {
      val now = new Timestamp((new java.util.Date()).getTime())
      Scene(
        UUID.randomUUID, // primary key
        now, // createdAt
        userId, // createdBy
        now, // modifiedAt
        userId, // modifiedBy
        organizationId,
        ingestSizeBytes,
        visibility,
        resolutionMeters,
        tags,
        datasource,
        sceneMetadata,
        cloudCover,
        acquisitionDate,
        thumbnailStatus,
        boundaryStatus,
        status,
        sunAzimuth,
        sunElevation,
        name,
        footprint
      )
    }
  }

  object Create {
    implicit val defaultThumbnailWithRelatedFormat = jsonFormat18(Create.apply)
  }

  case class WithRelated(
    id: UUID,
    createdAt: Timestamp,
    createdBy: String,
    modifiedAt: Timestamp,
    modifiedBy: String,
    organizationId: UUID,
    visibility: Visibility,
    resolutionMeters: Float,
    tags: List[String],
    datasource: String,
    sceneMetadata: Map[String, Any],
    cloudCover: Option[Float],
    acquisitionDate: Option[java.sql.Timestamp],
    thumbnailStatus: JobStatus,
    boundaryStatus: JobStatus,
    status: JobStatus,
    sunAzimuth: Option[Float],
    sunElevation: Option[Float],
    name: String,
    images: Seq[Image],
    footprint: Option[Projected[Geometry]],
    thumbnails: Seq[Thumbnail]
  )

  object WithRelated {
    implicit val defaultThumbnailWithRelatedFormat = jsonFormat22(WithRelated.apply)

    /** Helper function to create Iterable[Scene.WithRelated] from join
      *
      * It is necessary to map over the distinct scenes because that is the only way to
      * ensure that the sort order of the query result remains ordered after grouping
      *
      * @param records result of join query to return scene with related
      * information
      */
    def fromRecords(records: Seq[(Scene, Option[Image], Option[Thumbnail])]): Iterable[Scene.WithRelated] = {
      val distinctScenes = records.map(_._1).distinct
      val grouped = records.groupBy(_._1)
      distinctScenes.map{ scene =>
        // This should be relatively safe since scene is the key grouped by
        val (seqImages, seqThumbnails) = grouped(scene)
          .map { case (sr, im, th) => (im, th) }
          .unzip
        scene.withRelatedFromComponents(seqImages.flatten, seqThumbnails.flatten.distinct)
      }
    }
  }
}
