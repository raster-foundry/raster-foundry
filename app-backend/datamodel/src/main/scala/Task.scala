package com.rasterfoundry.datamodel

import com.rasterfoundry.datamodel.Task.TaskWithCampaign

import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.vector.{Geometry, Projected}
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.refined._

import java.sql.Timestamp
import java.util.UUID

@JsonCodec
final case class Review(
    vote: LabelVoteType,
    userName: String,
    description: Option[String]
)

case class Task(
    id: UUID,
    createdAt: Timestamp,
    createdBy: String,
    modifiedAt: Timestamp,
    owner: String,
    status: TaskStatus,
    lockedBy: Option[String],
    lockedOn: Option[Timestamp],
    geometry: Projected[Geometry],
    annotationProjectId: UUID,
    taskType: TaskType,
    parentTaskId: Option[UUID],
    reviews: Map[UUID, Review],
    reviewStatus: Option[TaskReviewStatus]
) {
  def toGeoJSONFeature(actions: List[TaskActionStamp]): Task.TaskFeature = {
    Task.TaskFeature(
      this.id,
      this.toProperties(actions),
      this.geometry
    )
  }

  def toProperties(actions: List[TaskActionStamp]): Task.TaskProperties = {
    // If task actions get out of sync from task status updates, it's possible
    // that the task will have a null note and a status of flagged. That's not great,
    // but I think preserving our freedom to fix things in the db if things go wrong
    // is more important than demanding consistency everywhere always.
    // It should be hard for them to get out of sync with the check constraint on task
    // actions, but you never know.
    // This grabs the note from the most recent task action, rather than the most recent
    // task action
    val statusNote: Option[NonEmptyString] =
      if (this.status == TaskStatus.Flagged) {
        actions
          .sortBy(-_.timestamp.toInstant.getEpochSecond)
          .headOption flatMap { _.note }
      } else None
    Task.TaskProperties(
      this.id,
      this.createdAt,
      this.createdBy,
      this.modifiedAt,
      this.owner,
      this.status,
      this.lockedBy,
      this.lockedOn,
      actions,
      this.annotationProjectId,
      statusNote,
      this.taskType,
      this.parentTaskId,
      this.reviews,
      this.reviewStatus
    )
  }

  def toTaskWithCampaign(campaignId: UUID): Task.TaskWithCampaign = {
    TaskWithCampaign(
      this.id,
      this.createdAt,
      this.createdBy,
      this.modifiedAt,
      this.owner,
      this.status,
      this.lockedBy,
      this.lockedOn,
      this.geometry,
      this.annotationProjectId,
      this.taskType,
      this.parentTaskId,
      this.reviews,
      this.reviewStatus,
      campaignId
    )
  }
}

object Task {

  final case class TaskProperties(
      id: UUID,
      createdAt: Timestamp,
      createdBy: String,
      modifiedAt: Timestamp,
      owner: String,
      status: TaskStatus,
      lockedBy: Option[String],
      lockedOn: Option[Timestamp],
      actions: List[TaskActionStamp],
      annotationProjectId: UUID,
      note: Option[NonEmptyString],
      taskType: TaskType,
      parentTaskId: Option[UUID],
      reviews: Map[UUID, Review],
      reviewStatus: Option[TaskReviewStatus]
  ) {
    def toCreate: TaskPropertiesCreate = {
      TaskPropertiesCreate(
        this.status,
        this.annotationProjectId,
        this.note,
        Some(this.taskType),
        this.parentTaskId,
        Some(this.reviews)
      )
    }
  }

  object TaskProperties {
    implicit val encTaskProperties: Encoder[TaskProperties] = deriveEncoder
    implicit val decTaskProperties: Decoder[TaskProperties] = deriveDecoder
  }

  final case class TaskWithCampaign(
      id: UUID,
      createdAt: Timestamp,
      createdBy: String,
      modifiedAt: Timestamp,
      owner: String,
      status: TaskStatus,
      lockedBy: Option[String],
      lockedOn: Option[Timestamp],
      geometry: Projected[Geometry],
      annotationProjectId: UUID,
      taskType: TaskType,
      parentTaskId: Option[UUID],
      reviews: Map[UUID, Review],
      reivewStatus: Option[TaskReviewStatus],
      campaignId: UUID
  ) {
    def toTask: Task = {
      Task(
        this.id,
        this.createdAt,
        this.createdBy,
        this.modifiedAt,
        this.owner,
        this.status,
        this.lockedBy,
        this.lockedOn,
        this.geometry,
        this.annotationProjectId,
        this.taskType,
        this.parentTaskId,
        this.reviews,
        this.reivewStatus
      )
    }

    def toProperties(
        actions: List[TaskActionStamp]
    ): TaskPropertiesWithCampaign = {
      val statusNote: Option[NonEmptyString] =
        if (this.status == TaskStatus.Flagged) {
          actions
            .sortBy(-_.timestamp.toInstant.getEpochSecond)
            .headOption flatMap { _.note }
        } else None
      Task.TaskPropertiesWithCampaign(
        this.id,
        this.createdAt,
        this.createdBy,
        this.modifiedAt,
        this.owner,
        this.status,
        this.lockedBy,
        this.lockedOn,
        actions,
        this.annotationProjectId,
        statusNote,
        this.taskType,
        this.parentTaskId,
        this.reviews,
        this.reivewStatus,
        this.campaignId
      )
    }

    def toGeoJSONFeature(
        actions: List[TaskActionStamp]
    ): Task.TaskFeatureWithCampaign = {
      Task.TaskFeatureWithCampaign(
        this.id,
        this.toProperties(actions),
        this.geometry
      )
    }
  }

  final case class TaskPropertiesWithCampaign(
      id: UUID,
      createdAt: Timestamp,
      createdBy: String,
      modifiedAt: Timestamp,
      owner: String,
      status: TaskStatus,
      lockedBy: Option[String],
      lockedOn: Option[Timestamp],
      actions: List[TaskActionStamp],
      annotationProjectId: UUID,
      note: Option[NonEmptyString],
      taskType: TaskType,
      parentTaskId: Option[UUID],
      reviews: Map[UUID, Review],
      reviewStatus: Option[TaskReviewStatus] = None,
      campaignId: UUID
  )

  object TaskPropertiesWithCampaign {
    implicit val encTaskPropertiesWithCampaign
      : Encoder[TaskPropertiesWithCampaign] = deriveEncoder
    implicit val decTaskPropertiesWithCamapgin
      : Decoder[TaskPropertiesWithCampaign] = deriveDecoder
  }

  final case class TaskPropertiesCreate(
      status: TaskStatus,
      annotationProjectId: UUID,
      note: Option[NonEmptyString],
      taskType: Option[TaskType],
      parentTaskId: Option[UUID],
      reviews: Option[Map[UUID, Review]],
      reviewStatus: Option[TaskReviewStatus] = None
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
      geometry: Projected[Geometry],
      _type: String = "Feature"
  )

  object TaskFeature {
    implicit val encTaskFeature: Encoder[TaskFeature] =
      Encoder.forProduct4("id", "type", "properties", "geometry")(tf =>
        (tf.id, tf._type, tf.properties, tf.geometry))
    implicit val decTaskFeature: Decoder[TaskFeature] =
      Decoder.forProduct4("id", "properties", "geometry", "type")(
        TaskFeature.apply _
      )
  }

  case class TaskFeatureWithCampaign(
      id: UUID,
      properties: TaskPropertiesWithCampaign,
      geometry: Projected[Geometry],
      _type: String = "Feature"
  )

  object TaskFeatureWithCampaign {
    implicit val encTaskFeature: Encoder[TaskFeatureWithCampaign] =
      Encoder.forProduct4("id", "type", "properties", "geometry")(tf =>
        (tf.id, tf._type, tf.properties, tf.geometry))
    implicit val decTaskFeature: Decoder[TaskFeatureWithCampaign] =
      Decoder.forProduct4("id", "properties", "geometry", "type")(
        TaskFeatureWithCampaign.apply _
      )
  }

  case class TaskFeatureCreate(
      properties: TaskPropertiesCreate,
      geometry: Projected[Geometry],
      _type: String = "Feature"
  ) {
    def withStatus(status: TaskStatus): TaskFeatureCreate =
      this.copy(properties = this.properties.copy(status = status))
  }

  object TaskFeatureCreate {
    implicit val decTaskFeatureCreate: Decoder[TaskFeatureCreate] =
      Decoder.forProduct3("properties", "geometry", "type")(
        TaskFeatureCreate.apply _
      )
    implicit val encTaskFeatureCreate: Encoder[TaskFeatureCreate] =
      Encoder.forProduct3("properties", "geometry", "type")(tfc =>
        (tfc.properties, tfc.geometry, tfc._type))
  }

  case class TaskFeatureCollection(
      _type: String = "FeatureCollection",
      features: List[TaskFeature]
  )

  object TaskFeatureCollection {
    implicit val encTaskFeatureCollection: Encoder[TaskFeatureCollection] =
      Encoder.forProduct2(
        "type",
        "features"
      )(
        tfc =>
          (
            tfc._type,
            tfc.features
        ))

    implicit val decTaskFeatureCollection: Decoder[TaskFeatureCollection] =
      Decoder.forProduct2(
        "type",
        "features"
      )(TaskFeatureCollection.apply _)
  }

  case class TaskFeatureCollectionCreate(
      _type: String = "FeatureCollection",
      features: List[TaskFeatureCreate]
  )

  object TaskFeatureCollectionCreate {
    implicit val decTaskFeatureCollectionCreate
      : Decoder[TaskFeatureCollectionCreate] =
      Decoder.forProduct2("type", "features")(
        TaskFeatureCollectionCreate.apply _
      )
    implicit val encTaskFeatureCollectionCreate
      : Encoder[TaskFeatureCollectionCreate] =
      Encoder.forProduct2("type", "features")(tfc => (tfc._type, tfc.features))
  }

  final case class TaskGridCreateProperties(
      sizeMeters: Option[Double]
  )

  object TaskGridCreateProperties {
    implicit val encTaskGridCreateProperties
      : Encoder[TaskGridCreateProperties] =
      deriveEncoder
    implicit val decTaskGridCreateProperties
      : Decoder[TaskGridCreateProperties] =
      deriveDecoder
  }

  case class TaskGridFeatureCreate(
      properties: TaskGridCreateProperties,
      geometry: Option[Projected[Geometry]],
      _type: String = "Feature"
  )

  object TaskGridFeatureCreate {
    implicit val decTaskGridFeatureCreate: Decoder[TaskGridFeatureCreate] =
      Decoder.forProduct3("properties", "geometry", "type")(
        TaskGridFeatureCreate.apply _
      )
    implicit val encTaskGridFeatureCreate: Encoder[TaskGridFeatureCreate] =
      Encoder.forProduct3("properties", "geometry", "type")(tfc =>
        (tfc.properties, tfc.geometry, tfc._type))
  }
}

final case class TaskUserSummary(
    userId: String,
    name: String,
    profileImageUri: String,
    labeledTaskCount: Int,
    labeledTaskAvgTimeSecond: Float,
    validatedTaskCount: Int,
    validatedTaskAvgTimeSecond: Float
)

object TaskUserSummary {
  implicit val taskUserSummaryEncoder: Encoder[TaskUserSummary] =
    deriveEncoder[TaskUserSummary]
}

@JsonCodec
final case class UnionedGeomExtent(
    geometry: Projected[Geometry],
    xMin: Double,
    yMin: Double,
    xMax: Double,
    yMax: Double
)

@JsonCodec
final case class UnionedGeomWithStatus(
    status: TaskStatus,
    geometry: Projected[Geometry]
)
