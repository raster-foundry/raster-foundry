package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import geotrellis.vector.Geometry
import geotrellis.slick.Projected

case class Scene(
  id: UUID,
  createdAt: java.sql.Timestamp,
  modifiedAt: java.sql.Timestamp,
  organizationId: UUID,
  createdBy: String,
  modifiedBy: String,
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
)

case class SceneWithRelated(
  id: UUID,
  createdBy: String,
  modifiedBy: String,
  createdAt: Timestamp,
  modifiedAt: Timestamp,
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

/** Helper object to create SceneWithRelated from case classes */
object SceneWithRelated {

  /** Helper constructor to create SceneWithRelated from case classes */
  def fromComponents(
    scene: Scene,
    images: Seq[Image],
    thumbnails: Seq[Thumbnail]
  ): SceneWithRelated = {
    SceneWithRelated(
      scene.id,
      scene.createdBy,
      scene.modifiedBy,
      scene.createdAt,
      scene.modifiedAt,
      scene.organizationId,
      scene.visibility,
      scene.resolutionMeters,
      scene.tags,
      scene.datasource,
      scene.sceneMetadata,
      scene.cloudCover,
      scene.acquisitionDate,
      scene.thumbnailStatus,
      scene.boundaryStatus,
      scene.status,
      scene.sunAzimuth,
      scene.sunElevation,
      scene.name,
      images,
      scene.footprint,
      thumbnails
    )
  }
}


/** Thumbnail class when posted with a scene */
case class SceneThumbnail(
  id: Option[UUID],
  thumbnailSize: ThumbnailSize,
  widthPx: Int,
  heightPx: Int,
  url: String
) {
  def toThumbnail(userId: String, scene: Scene): Thumbnail = {
    val now = new Timestamp((new java.util.Date()).getTime())
    Thumbnail(
      UUID.randomUUID, // primary key
      now, // createdAt
      now, // modifiedAt
      scene.organizationId,
      widthPx,
      heightPx,
      scene.id,
      url,
      thumbnailSize
    )
  }
}

/** Image class when posted with a scene */
case class SceneImage(
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

/** Case class extracted from a POST request */
case class CreateScene(
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
  images: List[SceneImage],
  footprint: Option[Projected[Geometry]],
  thumbnails: List[SceneThumbnail]
) {
  def toScene(userId: String): Scene = {
    val now = new Timestamp((new java.util.Date()).getTime())
    Scene(
      UUID.randomUUID, // primary key
      now, // createdAt
      now, // modifiedAt
      organizationId,
      userId, // createdBy
      userId, // modifiedBy
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