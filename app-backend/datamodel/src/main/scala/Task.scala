package com.rasterfoundry.datamodel

import geotrellis.vector.{Geometry, Projected}
import io.circe._
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}

import java.time.Instant
import java.util.UUID

case class Task(
    id: UUID,
    createdAt: Instant,
    createdBy: String,
    modifiedAt: Instant,
    modifiedBy: String,
    projectId: UUID,
    projectLayerId: UUID,
    status: TaskStatus,
    lockedBy: String,
    lockedOn: Instant,
    actions: List[TaskActionStamp],
    geometry: Option[Projected[Geometry]]
) extends GeoJSONSerializable[Task.TaskFeature] {
  def toGeoJSONFeature: Task.TaskFeature = {
    Task.TaskFeature(
      this.id,
      this.toProperties,
      this.geometry
    )
  }

  def toProperties: Task.TaskProperties = Task.TaskProperties(
    this.id,
    this.createdAt,
    this.createdBy,
    this.modifiedAt,
    this.modifiedBy,
    this.projectId,
    this.projectLayerId,
    this.status,
    this.lockedBy,
    this.lockedOn,
    this.actions
  )
}

object Task {

  final case class TaskProperties(
      id: UUID,
      createdAt: Instant,
      createdBy: String,
      modifiedAt: Instant,
      modifiedBy: String,
      projectId: UUID,
      projectLayerId: UUID,
      status: TaskStatus,
      lockedBy: String,
      lockedOn: Instant,
      actions: List[TaskActionStamp]
  )

  object TaskProperties {
    implicit val encTaskProperties: Encoder[TaskProperties] = deriveEncoder
    implicit val decTaskProperties: Decoder[TaskProperties] = deriveDecoder
  }

  case class TaskPropertiesCreate(
      projectId: UUID,
      projectLayerId: UUID,
      status: TaskStatus
  )

  object TaskPropertiesCreate {
    implicit val encTaskPropertiesCreate: Encoder[TaskPropertiesCreate] =
      deriveEncoder
    implicit val decTaskPropertiesCreate: Decoder[TaskPropertiesCreate] =
      deriveDecoder
  }

  case class TaskFeature(
      id: UUID,
      properties: TaskProperties,
      geometry: Option[Projected[Geometry]],
      _type: String = "Feature"
  ) extends GeoJSONFeature

  object TaskFeature {
    implicit val encTaskFeature: Encoder[TaskFeature] =
      Encoder.forProduct4("id", "type", "properties", "geometry")(
        tf => (tf.id, tf._type, tf.properties, tf.geometry)
      )
    implicit val decTaskFeature: Decoder[TaskFeature] =
      Decoder.forProduct4("id", "properties", "geometry", "type")(
        TaskFeature.apply _
      )
  }

  case class TaskFeatureCreate(
      properties: TaskPropertiesCreate,
      geometry: Option[Projected[Geometry]],
      _type: String = "Feature"
  )

  object TaskFeatureCreate {
    implicit val decTaskFeatureCreate: Decoder[TaskFeatureCreate] =
      Decoder.forProduct3("properties", "geometry", "type")(
        TaskFeatureCreate.apply _
      )
    implicit val encTaskFeatureCreate: Encoder[TaskFeatureCreate] =
      Encoder.forProduct3("properties", "geometry", "type")(
        tfc => (tfc.properties, tfc.geometry, tfc._type)
      )
  }

  case class TaskFeatureCollection(
      _type: String = "FeatureCollection",
      features: List[TaskFeature]
  )

  object TaskFeatureCollection {
    implicit val encTaskFeatureCollection: Encoder[TaskFeatureCollection] =
      Encoder.forProduct2("type", "features")(tfc => (tfc._type, tfc.features))

    implicit val decTaskFeatureCollection: Decoder[TaskFeatureCollection] =
      Decoder.forProduct2("type", "features")(TaskFeatureCollection.apply _)
  }
}
