package com.rasterfoundry.datamodel

import java.sql.Timestamp
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import geotrellis.vector.{Geometry, Projected, io => _}
import io.circe.generic.JsonCodec
import io.circe.generic.extras._
import io.circe.Encoder

@JsonCodec
final case class AnnotationFeatureCollectionCreate(
    features: Seq[Annotation.GeoJSONFeatureCreate]
)

@JsonCodec
final case class Annotation(id: UUID,
                            projectId: UUID,
                            createdAt: Timestamp,
                            createdBy: String,
                            modifiedAt: Timestamp,
                            modifiedBy: String,
                            owner: String,
                            label: String,
                            description: Option[String],
                            machineGenerated: Option[Boolean],
                            confidence: Option[Float],
                            quality: Option[AnnotationQuality],
                            geometry: Option[Projected[Geometry]],
                            annotationGroup: UUID,
                            labeledBy: Option[String],
                            verifiedBy: Option[String],
                            projectLayerId: UUID)
    extends GeoJSONSerializable[Annotation.GeoJSON] {
  def toGeoJSONFeature: Annotation.GeoJSON = Annotation.GeoJSON(
    this.id,
    this.geometry,
    AnnotationProperties(
      this.projectId,
      this.createdAt,
      this.createdBy,
      this.modifiedAt,
      this.modifiedBy,
      this.owner,
      this.label,
      this.description,
      this.machineGenerated,
      this.confidence,
      this.quality,
      this.annotationGroup,
      this.labeledBy,
      this.verifiedBy,
      this.projectLayerId
    )
  )
}

@JsonCodec
final case class AnnotationProperties(projectId: UUID,
                                      createdAt: Timestamp,
                                      createdBy: String,
                                      modifiedAt: Timestamp,
                                      modifiedBy: String,
                                      owner: String,
                                      label: String,
                                      description: Option[String],
                                      machineGenerated: Option[Boolean],
                                      confidence: Option[Float],
                                      quality: Option[AnnotationQuality],
                                      annotationGroup: UUID,
                                      labeledBy: Option[String] = None,
                                      verifiedBy: Option[String] = None,
                                      projectLayerId: UUID)

@JsonCodec
final case class AnnotationPropertiesCreate(owner: Option[String],
                                            label: String,
                                            description: Option[String],
                                            machineGenerated: Option[Boolean],
                                            confidence: Option[Float],
                                            quality: Option[AnnotationQuality],
                                            annotationGroup: Option[UUID],
                                            labeledBy: Option[String] = None,
                                            verifiedBy: Option[String] = None)

object Annotation extends LazyLogging {

  implicit val config: Configuration =
    Configuration.default.copy(transformMemberNames = {
      case "_type" => "type"
      case other   => other
    })

  def tupled = (Annotation.apply _).tupled
  def create = Create.apply _
  @ConfiguredJsonCodec
  final case class GeoJSON(id: UUID,
                           geometry: Option[Projected[Geometry]],
                           properties: AnnotationProperties,
                           _type: String = "Feature")
      extends GeoJSONFeature {
    def toAnnotation: Annotation = {
      Annotation(
        id,
        properties.projectId,
        properties.createdAt,
        properties.createdBy,
        properties.modifiedAt,
        properties.modifiedBy,
        properties.owner,
        properties.label match {
          case "" => "Unlabeled"
          case _  => properties.label
        },
        properties.description,
        properties.machineGenerated,
        properties.confidence,
        properties.quality,
        geometry,
        properties.annotationGroup,
        properties.labeledBy,
        properties.verifiedBy,
        properties.projectLayerId
      )
    }
  }

  @JsonCodec
  final case class Create(owner: Option[String],
                          label: String,
                          description: Option[String],
                          machineGenerated: Option[Boolean],
                          confidence: Option[Float],
                          quality: Option[AnnotationQuality],
                          geometry: Option[Projected[Geometry]],
                          annotationGroup: Option[UUID],
                          labeledBy: Option[String] = None,
                          verifiedBy: Option[String] = None)
      extends OwnerCheck {

    def toAnnotation(projectId: UUID,
                     user: User,
                     defaultAnnotationGroup: UUID,
                     projectLayerId: UUID): Annotation = {
      val now = new Timestamp(new java.util.Date().getTime)
      val ownerId = checkOwner(user, this.owner)
      Annotation(
        UUID.randomUUID, // id
        projectId, // projectId
        now, // createdAt
        user.id, // createdBy
        now, // modifiedAt
        user.id, // modifiedBy
        ownerId, // owner
        label match {
          case "" => "Unlabeled"
          case _  => label
        },
        description,
        machineGenerated,
        confidence,
        quality,
        geometry,
        annotationGroup.getOrElse(defaultAnnotationGroup),
        labeledBy,
        verifiedBy,
        projectLayerId
      )
    }
  }

  @JsonCodec
  final case class GeoJSONFeatureCreate(geometry: Option[Projected[Geometry]],
                                        properties: AnnotationPropertiesCreate)
      extends OwnerCheck {
    def toAnnotationCreate: Annotation.Create = {
      Annotation.Create(
        properties.owner,
        properties.label,
        properties.description,
        properties.machineGenerated,
        properties.confidence,
        properties.quality,
        geometry,
        properties.annotationGroup,
        properties.labeledBy,
        properties.verifiedBy
      )
    }
  }
}

@JsonCodec
final case class AnnotationWithOwnerInfo(id: UUID,
                                         projectId: UUID,
                                         createdAt: Timestamp,
                                         createdBy: String,
                                         modifiedAt: Timestamp,
                                         modifiedBy: String,
                                         owner: String,
                                         label: String,
                                         description: Option[String],
                                         machineGenerated: Option[Boolean],
                                         confidence: Option[Float],
                                         quality: Option[AnnotationQuality],
                                         geometry: Option[Projected[Geometry]],
                                         annotationGroup: UUID,
                                         labeledBy: Option[String],
                                         verifiedBy: Option[String],
                                         projectLayerId: UUID,
                                         ownerName: String,
                                         ownerProfileImageUri: String)
    extends GeoJSONSerializable[AnnotationWithOwnerInfo.GeoJSON] {
  def toGeoJSONFeature = AnnotationWithOwnerInfo.GeoJSON(
    this.id,
    this.geometry,
    AnnotationWithOwnerInfoProperties(
      this.projectId,
      this.createdAt,
      this.createdBy,
      this.modifiedAt,
      this.modifiedBy,
      this.owner,
      this.label,
      this.description,
      this.machineGenerated,
      this.confidence,
      this.quality,
      this.annotationGroup,
      this.labeledBy,
      this.verifiedBy,
      this.projectLayerId,
      this.ownerName,
      this.ownerProfileImageUri
    )
  )
}

object AnnotationWithOwnerInfo {

  implicit val config: Configuration =
    Configuration.default.copy(transformMemberNames = {
      case "_type" => "type"
      case other   => other
    })

  @ConfiguredJsonCodec
  final case class GeoJSON(id: UUID,
                           geometry: Option[Projected[Geometry]],
                           properties: AnnotationWithOwnerInfoProperties,
                           _type: String = "Feature")
      extends GeoJSONFeature
}

@JsonCodec
final case class AnnotationWithOwnerInfoProperties(
    projectId: UUID,
    createdAt: Timestamp,
    createdBy: String,
    modifiedAt: Timestamp,
    modifiedBy: String,
    owner: String,
    label: String,
    description: Option[String],
    machineGenerated: Option[Boolean],
    confidence: Option[Float],
    quality: Option[AnnotationQuality],
    annotationGroup: UUID,
    labeledBy: Option[String] = None,
    verifiedBy: Option[String] = None,
    projectLayerId: UUID,
    ownerName: String,
    ownerProfileImageUri: String)

final case class StacLabelItemProperties(
    property: List[String],
    classes: StacLabelItemProperties.StacLabelItemClasses,
    description: String,
    _type: String,
    datetime: Timestamp,
    title: Option[String] = None,
    task: Option[List[String]] = None,
    method: Option[List[String]] = None,
    version: Option[String] = None,
    summary: Option[StacLabelItemProperties.StacLabelSummary] = None
)

object StacLabelItemProperties {
  implicit val encodeStacLabelItemProperties: Encoder[StacLabelItemProperties] =
    Encoder.forProduct10(
      "label:property",
      "label:classes",
      "label:description",
      "label:type",
      "datetime",
      "title",
      "label:task",
      "label:method",
      "label:version",
      "label:summary"
    )(
      item =>
        (item.property,
         item.classes,
         item.description,
         item._type,
         item.datetime,
         item.title,
         item.task,
         item.method,
         item.version,
         item.summary))

  @JsonCodec
  final case class StacLabelItemClasses(
      name: String,
      classes: List[String]
  )

  @JsonCodec
  final case class StacLabelSummary(
      propertyKey: String,
      counts: Option[List[Count]] = None,
      statistics: Option[List[Statistics]] = None
  )

  @JsonCodec
  final case class Count(
      className: String,
      count: Int
  )

  @JsonCodec
  final case class Statistics(
      statName: String,
      value: Double
  )
}
