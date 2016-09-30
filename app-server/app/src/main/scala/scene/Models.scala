package com.azavea.rf.scene

import java.sql.Timestamp
import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import com.lonelyplanet.akka.http.extensions.PageRequest
import slick.lifted._
import spray.json._

import com.azavea.rf.AkkaSystem
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.datamodel.enums._
import com.azavea.rf.utils.{Database => DB, PaginatedResponse}
import com.azavea.rf.datamodel.driver.ExtendedPostgresDriver
import com.azavea.rf.footprint._
import geotrellis.vector.io._
import geotrellis.vector.Geometry
import geotrellis.slick.Projected


/** Scene with related information to organize response */
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
  images: Seq[ImagesRow],
  footprint: Option[Projected[Geometry]],
  thumbnails: Seq[ThumbnailsRow]
)

/** Helper object to create SceneWithRelated from case classes */
object SceneWithRelated {

  /** Helper constructor to create SceneWithRelated from case classes */
  def fromComponents(
    scene: ScenesRow,
    images: Seq[ImagesRow],
    thumbnails: Seq[ThumbnailsRow]
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
  def toThumbnailsRow(userId: String, scene: ScenesRow): ThumbnailsRow = {
    val now = new Timestamp((new java.util.Date()).getTime())
    ThumbnailsRow(
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
  def toImagesRow(userId: String, scene: ScenesRow): ImagesRow = {
    val now = new Timestamp((new java.util.Date()).getTime())
    ImagesRow(
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
  def toScene(userId: String): ScenesRow = {
    val now = new Timestamp((new java.util.Date()).getTime())
    ScenesRow(
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
