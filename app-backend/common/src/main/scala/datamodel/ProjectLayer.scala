package com.rasterfoundry.common.datamodel

import com.typesafe.scalalogging.LazyLogging
import geotrellis.vector.{Geometry, Projected}
import io.circe.generic.JsonCodec

import java.sql.Timestamp
import java.util.UUID

@JsonCodec
final case class ProjectLayer(
    id: UUID,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    name: String,
    projectId: Option[UUID],
    colorGroupHex: String,
    smartLayerId: Option[UUID],
    rangeStart: Option[Timestamp],
    rangeEnd: Option[Timestamp],
    geometry: Option[Projected[Geometry]],
    isSingleBand: Boolean,
    singleBandOptions: Option[SingleBandOptions.Params])
    extends GeoJSONSerializable[ProjectLayer.GeoJSON] {
  def toGeoJSONFeature: ProjectLayer.GeoJSON = ProjectLayer.GeoJSON(
    this.id,
    this.geometry,
    ProjectLayerProperties(
      this.projectId,
      this.createdAt,
      this.modifiedAt,
      this.name,
      this.colorGroupHex,
      this.smartLayerId,
      this.rangeStart,
      this.rangeEnd,
      this.isSingleBand,
      this.singleBandOptions
    )
  )
}

@JsonCodec
final case class ProjectLayerProperties(
    projectId: Option[UUID],
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    name: String,
    colorGroupHex: String,
    smartLayerId: Option[UUID],
    rangeStart: Option[Timestamp],
    rangeEnd: Option[Timestamp],
    isSingleBand: Boolean,
    singleBandOptions: Option[SingleBandOptions.Params])

object ProjectLayer extends LazyLogging {
  def create = Create.apply _

  @JsonCodec
  final case class Create(name: String,
                          projectId: Option[UUID],
                          colorGroupHex: String,
                          smartLayerId: Option[UUID],
                          rangeStart: Option[Timestamp],
                          rangeEnd: Option[Timestamp],
                          geometry: Option[Projected[Geometry]],
                          isSingleBand: Boolean,
                          singleBandOptions: Option[SingleBandOptions.Params]) {
    def toProjectLayer: ProjectLayer = {
      val now = new Timestamp(new java.util.Date().getTime)
      ProjectLayer(
        UUID.randomUUID,
        now,
        now,
        this.name,
        this.projectId,
        this.colorGroupHex,
        this.smartLayerId,
        this.rangeStart,
        this.rangeEnd,
        this.geometry,
        this.isSingleBand,
        this.singleBandOptions
      )
    }
  }
  final case class GeoJSON(id: UUID,
                           geometry: Option[Projected[Geometry]],
                           properties: ProjectLayerProperties,
                           _type: String = "Feature")
      extends GeoJSONFeature {
    def toProjectLayer: ProjectLayer = {
      ProjectLayer(
        id,
        properties.createdAt,
        properties.modifiedAt,
        properties.name,
        properties.projectId,
        properties.colorGroupHex,
        properties.smartLayerId,
        properties.rangeStart,
        properties.rangeEnd,
        geometry,
        properties.isSingleBand,
        properties.singleBandOptions
      )
    }
  }
}
