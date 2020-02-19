package com.rasterfoundry.datamodel

import cats.data.NonEmptyList
import geotrellis.vector.{Geometry, Projected, io => _}
import io.circe.Encoder
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.extras._
import io.circe.generic.semiauto._
import io.circe.syntax._

import java.sql.Timestamp
import java.util.UUID

final case class AnnotationLabel(
    id: UUID,
    createdAt: Timestamp,
    createdBy: String,
    geometry: Option[Projected[Geometry]],
    annotationProjectId: UUID,
    annotationTaskId: UUID
)

final case class AnnotationLabelProperties(
    createdAt: Timestamp,
    createdBy: String,
    annotationProjectId: UUID,
    annotationTaskId: UUID
)

final case class AnnotationLabelPropertiesCreate(
    annotationProjectId: UUID,
    annotationTaskId: UUID
)

@JsonCodec
final case class AnnotationLabelWithClassesFeatureCollectionCreate(
    features: Seq[AnnotationLabelWithClasses.GeoJSONFeatureCreate]
)

@JsonCodec
final case class AnnotationLabelWithClasses(
    id: UUID,
    createdAt: Timestamp,
    createdBy: String,
    geometry: Option[Projected[Geometry]],
    annotationProjectId: UUID,
    annotationTaskId: UUID,
    annotationLabelClasses: NonEmptyList[UUID]
) extends GeoJSONSerializable[AnnotationLabelWithClasses.GeoJSON] {
  def toGeoJSONFeature = AnnotationLabelWithClasses.GeoJSON(
    this.id,
    this.geometry,
    AnnotationLabelWithClassesProperties(
      this.createdAt,
      this.createdBy,
      this.annotationProjectId,
      this.annotationTaskId,
      this.annotationLabelClasses
    )
  )
}

object AnnotationLabelWithClasses {
  implicit val config: Configuration =
    Configuration.default.copy(transformMemberNames = {
      case "_type" => "type"
      case other   => other
    })
  def tupled = (AnnotationLabel.apply _).tupled

  final case class Create(
      geometry: Option[Projected[Geometry]],
      annotationLabelClasses: NonEmptyList[UUID]
  ) {
    def toAnnotationLabelWithClasses(
        annotationProjectId: UUID,
        annotationTaskId: UUID,
        user: User
    ): AnnotationLabelWithClasses = {
      val now = new Timestamp(new java.util.Date().getTime)
      AnnotationLabelWithClasses(
        UUID.randomUUID,
        now,
        user.id,
        geometry,
        annotationProjectId,
        annotationTaskId,
        annotationLabelClasses
      )
    }
  }

  @ConfiguredJsonCodec
  final case class GeoJSON(
      id: UUID,
      geometry: Option[Projected[Geometry]],
      properties: AnnotationLabelWithClassesProperties,
      _type: String = "Feature"
  ) extends GeoJSONFeature

  @JsonCodec
  final case class GeoJSONFeatureCreate(
      geometry: Option[Projected[Geometry]],
      properties: AnnotationLabelWithClassesPropertiesCreate
  ) {
    def toAnnotationLabelWithClassesCreate
      : AnnotationLabelWithClasses.Create = {
      AnnotationLabelWithClasses.Create(
        geometry,
        properties.annotationLabelClasses
      )
    }
  }

  object GeoJSON {
    implicit val annoLabelWithClassesGeojonEncoder: Encoder[GeoJSON] =
      Encoder.forProduct4("id", "geometry", "properties", "type")(
        geojson =>
          (geojson.id, geojson.geometry, geojson.properties, geojson._type)
      )
  }
}

@JsonCodec
final case class AnnotationLabelWithClassesPropertiesCreate(
    annotationLabelClasses: NonEmptyList[UUID]
)

@JsonCodec
final case class AnnotationLabelWithClassesProperties(
    createdAt: Timestamp,
    createdBy: String,
    annotationProjectId: UUID,
    annotationTaskId: UUID,
    annotationLabelClasses: NonEmptyList[UUID]
)

object AnnotationLabelWithClassesProperties {
  implicit val annotationLabelWithClassesPropertiesEncoder
    : Encoder[AnnotationLabelWithClassesProperties] =
    new Encoder[AnnotationLabelWithClassesProperties] {
      final def apply(
          properties: AnnotationLabelWithClassesProperties
      ): Json = {
        (
          Map(
            "createdAt" -> properties.createdAt.asJson,
            "createdBy" -> properties.createdBy.asJson,
            "annotationProjectId" -> properties.annotationProjectId.asJson,
            "annotationTaskId" -> properties.annotationTaskId.asJson,
            "annotationLabelClasses" -> properties.annotationLabelClasses.asJson
          )
        ).asJson
      }
    }
}

final case class AnnotationLabelWithClassesFeatureCollection(
    features: List[AnnotationLabelWithClasses.GeoJSON],
    `type`: String = "FeatureCollection"
)

object AnnotationLabelWithClassesFeatureCollection {
  implicit val annoLabelWithClassesFCEncoder
    : Encoder[AnnotationLabelWithClassesFeatureCollection] =
    deriveEncoder[AnnotationLabelWithClassesFeatureCollection]
}
