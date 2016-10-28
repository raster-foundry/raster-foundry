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
  footprint: Option[Projected[Geometry]] = None,
  metadataFiles: List[String]
) {
  def toScene = this

  def withRelatedFromComponents(
    images: Seq[Image.WithRelated],
    thumbnails: Seq[Thumbnail]
  ): Scene.WithRelated = Scene.withRelated(
    this.id,
    this.createdAt,
    this.createdBy,
    this.modifiedAt,
    this.modifiedBy,
    this.organizationId,
    this.visibility,
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
    this.footprint,
    this.metadataFiles,
    images,
    thumbnails
  )
}


object Scene extends GeoJsonSupport {

  def tupled = (Scene.apply _).tupled

  def create = Create.apply _

  def withRelated = WithRelated.apply _

  implicit val defaultSceneFormat = jsonFormat21(Scene.apply)


  /** Case class extracted from a POST request */
  case class Create(
    id: Option[UUID],
    organizationId: UUID,
    ingestSizeBytes: Int,
    visibility: Visibility,
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
    footprint: Option[Projected[Geometry]],
    metadataFiles: List[String],
    images: List[Image.Banded],
    thumbnails: List[Thumbnail.Identified]
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
        status,
        sunAzimuth,
        sunElevation,
        name,
        footprint,
        metadataFiles
      )
    }
  }

  object Create {
    implicit val defaultThumbnailWithRelatedFormat = jsonFormat19(Create.apply)
  }

  case class WithRelated(
    id: UUID,
    createdAt: Timestamp,
    createdBy: String,
    modifiedAt: Timestamp,
    modifiedBy: String,
    organizationId: UUID,
    visibility: Visibility,
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
    footprint: Option[Projected[Geometry]],
    metadataFiles: List[String],
    images: Seq[Image.WithRelated],
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
    def fromRecords(records: Seq[(Scene, Option[Image], Option[Band], Option[Thumbnail])]): Iterable[Scene.WithRelated] = {
      val distinctScenes = records.map(_._1).distinct
      val groupedThumbs = records.map(_._4).flatten.groupBy(_.sceneId)
      val groupedImages = Image.WithRelated.fromRecords {
        val images = records.map(_._2).flatten
        val bands = records.map(_._3).flatten
        for {
          im <- images
          bd <- bands
        } yield (im, bd)
      }.groupBy(_.scene)
      distinctScenes.map { scene =>
        val seqImages = groupedImages.get(scene.id) match {
          case Some(result) => result.asInstanceOf[Seq[Image.WithRelated]]
          case _ => List.empty[Image.WithRelated]
        }
        val seqThumbnails = groupedThumbs.get(scene.id) match {
          case Some(result) => result.asInstanceOf[Seq[Thumbnail]]
          case _ => List.empty[Thumbnail]
        }
        scene.withRelatedFromComponents(seqImages, seqThumbnails.distinct)
      }
    }
  }
}
