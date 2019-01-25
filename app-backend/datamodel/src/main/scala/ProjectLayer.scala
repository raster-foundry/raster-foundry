package com.rasterfoundry.datamodel
import java.sql.Timestamp
import java.util.UUID

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import cats.syntax.either._
import com.rasterfoundry.bridge._
import geotrellis.vector.io.json.GeoJsonSupport
import geotrellis.vector.{Geometry, Projected}
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto._
import io.circe.syntax._

@JsonCodec
final case class ProjectLayer(id: UUID,
                              createdAt: Timestamp,
                              modifiedAt: Timestamp,
                              name: String,
                              projectId: Option[UUID],
                              colorGroupHex: String,
                              smartLayerId: Option[UUID],
                              rangeStart: Option[Timestamp],
                              rangeEnd: Option[Timestamp],
                              geometry: Option[Projected[Geometry]])
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
      this.rangeEnd
    )
  )
}

@JsonCodec
final case class ProjectLayerProperties(projectId: Option[UUID],
                                        createdAt: Timestamp,
                                        modifiedAt: Timestamp,
                                        name: String,
                                        colorGroupHex: String,
                                        smartLayerId: Option[UUID],
                                        rangeStart: Option[Timestamp],
                                        rangeEnd: Option[Timestamp])

object ProjectLayer extends LazyLogging {
  def create = Create.apply _

  @JsonCodec
  final case class Create(name: String,
                          projectId: Option[UUID],
                          colorGroupHex: String,
                          smartLayerId: Option[UUID],
                          rangeStart: Option[Timestamp],
                          rangeEnd: Option[Timestamp],
                          geometry: Option[Projected[Geometry]]) {
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
        this.geometry
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
        geometry
      )
    }
  }
}
