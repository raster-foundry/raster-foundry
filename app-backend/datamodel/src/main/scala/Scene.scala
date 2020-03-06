package com.rasterfoundry.datamodel

import geotrellis.proj4.CRS
import geotrellis.raster.{CellType, GridExtent}
import geotrellis.vector.{MultiPolygon, Projected}
import io.circe._
import io.circe.generic.JsonCodec

import java.net.{URI, URLDecoder}
import java.sql.Timestamp
import java.util.UUID

@JsonCodec
final case class SceneFilterFields(
    cloudCover: Option[Float] = None,
    acquisitionDate: Option[java.sql.Timestamp] = None,
    sunAzimuth: Option[Float] = None,
    sunElevation: Option[Float] = None
)

object SceneFilterFields {
  def tupled = (SceneFilterFields.apply _).tupled

  type TupleType =
    (Option[Float], Option[java.sql.Timestamp], Option[Float], Option[Float])
}

@JsonCodec
final case class SceneStatusFields(
    thumbnailStatus: JobStatus,
    boundaryStatus: JobStatus,
    ingestStatus: IngestStatus
)

@JsonCodec
final case class SceneMetadataFields(
    dataPath: Option[String] = None,
    crs: Option[CRS] = None,
    bandCount: Option[Int] = None,
    cellType: Option[CellType] = None,
    gridExtent: Option[GridExtent[Long]] = None,
    resolutions: Option[List[GridExtent[Long]]] = None,
    noDataValue: Option[Double] = None
)

object SceneStatusFields {
  def tupled = (SceneStatusFields.apply _).tupled

  type TupleType = (JobStatus, JobStatus, IngestStatus)
}

@JsonCodec
final case class Scene(
    id: UUID,
    createdAt: java.sql.Timestamp,
    createdBy: String,
    modifiedAt: java.sql.Timestamp,
    owner: String,
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
    statusFields: SceneStatusFields,
    sceneType: Option[SceneType] = None,
    metadataFields: SceneMetadataFields = new SceneMetadataFields()
) {
  def toScene: Scene = this

  def withRelatedFromComponents(
      images: List[Image.WithRelated],
      thumbnails: List[Thumbnail],
      datasource: Datasource
  ): Scene.WithRelated =
    Scene.WithRelated(
      this.id,
      this.createdAt,
      this.createdBy,
      this.modifiedAt,
      this.owner,
      this.visibility,
      this.tags,
      datasource.toThin,
      this.sceneMetadata,
      this.name,
      this.tileFootprint,
      this.dataFootprint,
      this.metadataFiles,
      images,
      thumbnails,
      this.ingestLocation,
      this.filterFields,
      this.statusFields,
      this.sceneType,
      this.metadataFields
    )

  def browseFromComponents(
      thumbnails: List[Thumbnail],
      datasource: Datasource,
      inProject: Option[Boolean],
      inLayer: Option[Boolean]
  ): Scene.Browse = Scene.Browse(
    this.id,
    this.createdAt,
    this.createdBy,
    this.modifiedAt,
    this.owner,
    this.visibility,
    this.tags,
    datasource.toThin,
    this.sceneMetadata,
    this.name,
    this.tileFootprint,
    this.dataFootprint,
    this.metadataFiles,
    thumbnails,
    this.ingestLocation,
    this.filterFields,
    this.statusFields,
    this.sceneType,
    inProject,
    inLayer,
    this.metadataFields
  )

  def projectSceneFromComponents(
      thumbnails: List[Thumbnail],
      datasource: Datasource,
      sceneOrder: Option[Int]
  ): Scene.ProjectScene = Scene.ProjectScene(
    this.id,
    this.createdAt,
    this.createdBy,
    this.modifiedAt,
    this.owner,
    this.visibility,
    this.tags,
    datasource.toThin,
    this.sceneMetadata,
    this.name,
    this.tileFootprint,
    this.dataFootprint,
    this.metadataFiles,
    thumbnails.toList,
    this.ingestLocation,
    this.filterFields,
    this.statusFields,
    this.sceneType,
    sceneOrder,
    this.metadataFields
  )

  // Lifted from ProjectDao removeLayerOverview method --
  // it's not clear what sort of common place this URI parsing logic should live in
  // so it's duplicated here
  def bucketAndKey: Option[(String, String)] = ingestLocation map { ingestLoc =>
    val uri = URI.create(ingestLoc)
    val urlPath = uri.getPath()
    val bucket = URLDecoder.decode(uri.getHost(), "UTF-8")
    val key = URLDecoder.decode(urlPath.drop(1), "UTF-8")
    (bucket, key)
  }
}

object Scene {

  def cacheKey(id: UUID) = s"Scene:$id"

  /** Case class extracted from a POST request */
  @JsonCodec
  final case class Create(
      id: Option[UUID],
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
      statusFields: SceneStatusFields,
      sceneType: Option[SceneType] = None,
      metadataFields: Option[SceneMetadataFields] = None
  ) extends OwnerCheck {
    def toScene(user: User): Scene = {
      val now = new Timestamp(new java.util.Date().getTime)

      val ownerId = checkOwner(user, this.owner)

      Scene(
        id.getOrElse(UUID.randomUUID),
        now, // createdAt
        user.id, // createdBy
        now, // modifiedAt
        ownerId, // owner
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
        statusFields,
        sceneType,
        metadataFields.getOrElse(new SceneMetadataFields)
      )
    }
  }

  @JsonCodec
  final case class WithRelated(
      id: UUID,
      createdAt: Timestamp,
      createdBy: String,
      modifiedAt: Timestamp,
      owner: String,
      visibility: Visibility,
      tags: List[String],
      datasource: Datasource.Thin,
      sceneMetadata: Json,
      name: String,
      tileFootprint: Option[Projected[MultiPolygon]],
      dataFootprint: Option[Projected[MultiPolygon]],
      metadataFiles: List[String],
      images: List[Image.WithRelated],
      thumbnails: List[Thumbnail],
      ingestLocation: Option[String],
      filterFields: SceneFilterFields = new SceneFilterFields(),
      statusFields: SceneStatusFields,
      sceneType: Option[SceneType] = None,
      metadataFields: SceneMetadataFields = new SceneMetadataFields(),
  ) {
    def toScene: Scene =
      Scene(
        id,
        createdAt,
        createdBy,
        modifiedAt,
        owner,
        visibility,
        tags,
        datasource.id,
        sceneMetadata,
        name,
        tileFootprint,
        dataFootprint,
        metadataFiles,
        ingestLocation,
        filterFields,
        statusFields,
        sceneType,
        metadataFields
      )
  }

  @JsonCodec
  final case class Browse(
      id: UUID,
      createdAt: Timestamp,
      createdBy: String,
      modifiedAt: Timestamp,
      owner: String,
      visibility: Visibility,
      tags: List[String],
      datasource: Datasource.Thin,
      sceneMetadata: Json,
      name: String,
      tileFootprint: Option[Projected[MultiPolygon]],
      dataFootprint: Option[Projected[MultiPolygon]],
      metadataFiles: List[String],
      thumbnails: List[Thumbnail],
      ingestLocation: Option[String],
      filterFields: SceneFilterFields = new SceneFilterFields(),
      statusFields: SceneStatusFields,
      sceneType: Option[SceneType] = None,
      inProject: Option[Boolean] = None,
      inLayer: Option[Boolean] = None,
      metadataFields: SceneMetadataFields = new SceneMetadataFields()
  ) {
    def toScene: Scene =
      Scene(
        id,
        createdAt,
        createdBy,
        modifiedAt,
        owner,
        visibility,
        tags,
        datasource.id,
        sceneMetadata,
        name,
        tileFootprint,
        dataFootprint,
        metadataFiles,
        ingestLocation,
        filterFields,
        statusFields,
        sceneType,
        metadataFields
      )
  }

  @JsonCodec
  final case class ProjectScene(
      id: UUID,
      createdAt: Timestamp,
      createdBy: String,
      modifiedAt: Timestamp,
      owner: String,
      visibility: Visibility,
      tags: List[String],
      datasource: Datasource.Thin,
      sceneMetadata: Json,
      name: String,
      tileFootprint: Option[Projected[MultiPolygon]],
      dataFootprint: Option[Projected[MultiPolygon]],
      metadataFiles: List[String],
      thumbnails: List[Thumbnail],
      ingestLocation: Option[String],
      filterFields: SceneFilterFields = new SceneFilterFields(),
      statusFields: SceneStatusFields,
      sceneType: Option[SceneType] = None,
      sceneOrder: Option[Int],
      metadataFields: SceneMetadataFields = new SceneMetadataFields()
  )
}
