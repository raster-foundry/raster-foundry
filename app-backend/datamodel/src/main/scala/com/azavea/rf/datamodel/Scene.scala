package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import com.azavea.rf.bridge._
import geotrellis.vector.Geometry
import geotrellis.slick.Projected

import io.circe._
import io.circe.generic.JsonCodec

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
    images,
    thumbnails,
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
    tileFootprint: Option[Projected[Geometry]],
    dataFootprint: Option[Projected[Geometry]],
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
    tileFootprint: Option[Projected[Geometry]],
    dataFootprint: Option[Projected[Geometry]],
    metadataFiles: List[String],
    images: Seq[Image.WithRelated],
    thumbnails: Seq[Thumbnail],
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
    /** Helper function to create Iterable[Scene.WithRelated] from join
      *
      * It is necessary to map over the distinct scenes because that is the only way to
      * ensure that the sort order of the query result remains ordered after grouping
      *
      * @param records result of join query to return scene with related
      * information
      */
    @SuppressWarnings(Array("TraversableHead"))
    def fromRecords(records: Seq[(Scene, Option[Image], Option[Band], Option[Thumbnail])])
      : Iterable[Scene.WithRelated] = {
      val distinctScenes = records.map(_._1.id).distinct
      val groupedScenes = records.map(_._1).groupBy(_.id)
      val groupedRecords = records.groupBy(_._1.id)
      val groupedBands = records.flatMap(_._3).distinct.groupBy(_.image)

      distinctScenes.map { scene =>
        val (seqImages, seqThumbnails) = groupedRecords(scene).map {
          case (_, image, _, thumbnail) => (image, thumbnail)
        }.unzip
        val imagesWithComponents: Seq[Image.WithRelated] = seqImages.flatten.distinct.map {
          image => image.withRelatedFromComponents(groupedBands.getOrElse(image.id, Seq[Band]()))
        }
        groupedScenes.get(scene) match {
          case Some(scene) => scene.head.withRelatedFromComponents(
            imagesWithComponents, seqThumbnails.flatten.distinct
          )
          case _ => throw new Exception("This is impossible")
        }
      }
    }
  }
}
