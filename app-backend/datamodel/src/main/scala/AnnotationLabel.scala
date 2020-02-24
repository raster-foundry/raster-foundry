package com.rasterfoundry.datamodel

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

@JsonCodec
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
    annotationLabelClasses: List[UUID]
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

  def toStacGeoJSONFeature(
      classToGroup: Map[UUID, String],
      classes: Map[UUID, String]
  ): AnnotationLabelWithClasses.StacGeoJSON = {
    val classMap: Map[String, String] =
      this.annotationLabelClasses
        .map { classId =>
          for {
            group <- classToGroup.get(classId)
            labelClass <- classes.get(classId)
          } yield (group -> labelClass)
        }
        .flatten
        .toMap
    AnnotationLabelWithClasses.StacGeoJSON(
      this.id,
      this.geometry,
      AnnotationLabelProperties(
        this.createdAt,
        this.createdBy,
        this.annotationProjectId,
        this.annotationTaskId
      ),
      classMap
    )
  }
}

object AnnotationLabelWithClasses {
  implicit val config: Configuration =
    Configuration.default.copy(transformMemberNames = {
      case "_type" => "type"
      case other   => other
    })

  final case class Create(
      geometry: Option[Projected[Geometry]],
      annotationLabelClasses: List[UUID]
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

  /**
    * Classes on STAC geojson labels must be top level items in the properties.
    *
    * Class fields (which are AnnotationLabelClassGroup names)
    * under properties are specified on the StacItem which refers to it.
    * The optional values for these keys are the actual class of the label.
    *
    * In the following example, we have 3 class groups defined: label, color, and redTint.
    * The StacItem referring to it would have label:properties field containing ["label", "color", "redTint"]
    * ex:
    * {
    *   id: ...,
    *   geometry: ...,
    *   type: "Feature",
    *   properties: {
    *     createdAt: ...,
    *     createdBy: ...,
    *     annotationProjectId: ...,
    *     annotationTaskId: ...,
    *     label: "something", -- we should default to "label" as the class group name for single class labels
    *     color: "blue", -- color is the group, "blue" is the class selected
    *     redTint: null -- redTint is the group, and the value is null because "blue" is the color selected.
    *                      This is what a discriminant would look like
    *   }
    * }
    */
  final case class StacGeoJSON(
      id: UUID,
      geometry: Option[Projected[Geometry]],
      properties: AnnotationLabelProperties,
      classMap: Map[String, String],
      _type: String = "Feature"
  ) extends GeoJSONFeature

  object StacGeoJSON {
    implicit val stacGeoJsonEncoder: Encoder[StacGeoJSON] =
      Encoder.forProduct3("geometry", "type", "properties")(
        geojson =>
          (
            geojson.geometry,
            geojson._type,
            (
              Map(
                "id" -> geojson.id.asJson,
                "createdAt" -> geojson.properties.createdAt.asJson,
                "createdBy" -> geojson.properties.createdBy.asJson,
                "annotationProjectId" -> geojson.properties.annotationProjectId.asJson,
                "annotationTaskId" -> geojson.properties.annotationTaskId.asJson
              ) ++ geojson.classMap.map(e => (e._1 -> e._2.asJson))
            )
        )
      )
  }
}

@JsonCodec
final case class AnnotationLabelWithClassesPropertiesCreate(
    annotationLabelClasses: List[UUID]
)

@JsonCodec
final case class AnnotationLabelWithClassesProperties(
    createdAt: Timestamp,
    createdBy: String,
    annotationProjectId: UUID,
    annotationTaskId: UUID,
    annotationLabelClasses: List[UUID]
)

final case class StacGeoJSONFeatureCollection(
    features: List[AnnotationLabelWithClasses.StacGeoJSON],
    `type`: String = "FeatureCollection"
)

object StacGeoJSONFeatureCollection {
  implicit val stacGeoJsonFcEncoder: Encoder[StacGeoJSONFeatureCollection] =
    deriveEncoder[StacGeoJSONFeatureCollection]
}
