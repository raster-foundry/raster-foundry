package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import geotrellis.vector.{MultiPolygon, Projected}
import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
final case class SceneFilterFields(cloudCover: Option[Float] = None,
                                   acquisitionDate: Option[java.sql.Timestamp] =
                                     None,
                                   sunAzimuth: Option[Float] = None,
                                   sunElevation: Option[Float] = None)

object SceneFilterFields {
  def tupled = (SceneFilterFields.apply _).tupled

  type TupleType =
    (Option[Float], Option[java.sql.Timestamp], Option[Float], Option[Float])
}

@JsonCodec
final case class SceneStatusFields(thumbnailStatus: JobStatus,
                                   boundaryStatus: JobStatus,
                                   ingestStatus: IngestStatus)

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
    modifiedBy: String,
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
    sceneType: Option[SceneType] = None
) {
  def toScene: Scene = this

  def withRelatedFromComponents(images: List[Image.WithRelated],
                                thumbnails: List[Thumbnail],
                                datasource: Datasource): Scene.WithRelated =
    Scene.WithRelated(
      this.id,
      this.createdAt,
      this.createdBy,
      this.modifiedAt,
      this.modifiedBy,
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
      this.sceneType
    )

  def browseFromComponents(
      thumbnails: List[Thumbnail],
      datasource: Datasource,
      inProject: Option[Boolean]
  ): Scene.Browse = Scene.Browse(
    this.id,
    this.createdAt,
    this.createdBy,
    this.modifiedAt,
    this.modifiedBy,
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
    inProject
  )

  def projectSceneFromComponents(
      thumbnails: List[Thumbnail],
      datasource: Datasource,
      sceneToProject: Option[SceneToProject]
  ): Scene.ProjectScene = Scene.ProjectScene(
    this.id,
    this.createdAt,
    this.createdBy,
    this.modifiedAt,
    this.modifiedBy,
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
    sceneToProject.map(_.sceneOrder).flatten
  )
}

object Scene {

  /** Case class extracted from a POST request */
  @JsonCodec
  final case class Create(id: Option[UUID],
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
                          filterFields: SceneFilterFields =
                            new SceneFilterFields(),
                          statusFields: SceneStatusFields,
                          sceneType: Option[SceneType] = None)
      extends OwnerCheck {
    def toScene(user: User): Scene = {
      val now = new Timestamp(new java.util.Date().getTime)

      val ownerId = checkOwner(user, this.owner)

      Scene(
        id.getOrElse(UUID.randomUUID),
        now, // createdAt
        user.id, // createdBy
        now, // modifiedAt
        user.id, // modifiedBy
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
        sceneType
      )
    }
  }

  @JsonCodec
  final case class WithRelated(id: UUID,
                               createdAt: Timestamp,
                               createdBy: String,
                               modifiedAt: Timestamp,
                               modifiedBy: String,
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
                               filterFields: SceneFilterFields =
                                 new SceneFilterFields(),
                               statusFields: SceneStatusFields,
                               sceneType: Option[SceneType] = None) {
    def toScene: Scene =
      Scene(
        id,
        createdAt,
        createdBy,
        modifiedAt,
        modifiedBy,
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
        sceneType
      )
  }

  @JsonCodec
  final case class Browse(
      id: UUID,
      createdAt: Timestamp,
      createdBy: String,
      modifiedAt: Timestamp,
      modifiedBy: String,
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
      inProject: Option[Boolean] = None
  ) {
    def toScene: Scene =
      Scene(
        id,
        createdAt,
        createdBy,
        modifiedAt,
        modifiedBy,
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
        sceneType
      )
  }

  @JsonCodec
  case class ProjectScene(
      id: UUID,
      createdAt: Timestamp,
      createdBy: String,
      modifiedAt: Timestamp,
      modifiedBy: String,
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
      sceneOrder: Option[Int]
  )
}
