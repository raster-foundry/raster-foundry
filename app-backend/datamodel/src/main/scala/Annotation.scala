package com.rasterfoundry.datamodel

import java.sql.Timestamp
import java.util.UUID

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import com.vividsolutions.jts.{geom => jts}
import geotrellis.geotools._
import geotrellis.proj4.{CRS, WebMercator}
import geotrellis.vector.{Geometry, Projected, io => _, _}
import geotrellis.vector.reproject.Reproject
import io.circe.generic.JsonCodec
import io.circe.generic.extras._
import io.circe.parser._
import org.geotools.referencing.{CRS => geotoolsCRS}
import org.opengis.feature.Property
import org.opengis.feature.simple.SimpleFeature
import org.opengis.referencing.crs.CoordinateReferenceSystem

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
                            verifiedBy: Option[String])
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
      this.verifiedBy
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
                                      verifiedBy: Option[String] = None)

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

  @SuppressWarnings(Array("NullParameter"))
  def fromSimpleFeatureWithProps(sf: SimpleFeature,
                                 fields: Map[String, String],
                                 userId: String,
                                 prj: String): Option[Create] = {
    // get the projection from the passed string in .prj file
    // then get its EPSG code to retrieve its CRS in geotrellis as the start CRS
    val startCsr: CoordinateReferenceSystem = geotoolsCRS.parseWKT(prj)
    val startEpsgCode: Int =
      geotoolsCRS.lookupIdentifier(startCsr, true).replace("EPSG:", "").toInt
    val geom: Geometry = sf.toGeometry[Geometry]
    val projected = Projected(
      Reproject(geom, CRS.fromEpsgCode(startEpsgCode), WebMercator),
      3857
    )

    val labelPropName = fields.getOrElse("label", null)
    val desPropName = fields.getOrElse("description", null)
    val machinePropName = fields.getOrElse("isMachine", null)

    val label = labelPropName match {
      case null => "Unlabeled"
      case _    => sf.getProperty(labelPropName).getValue.toString
    }
    val description = desPropName match {
      case null => Some("No Description")
      case _    => Some(sf.getProperty(desPropName).getValue.toString)
    }
    val (isMachine, confidence, quality) = machinePropName match {
      case null => (Some(false), None, None)
      case _ =>
        val isM = Option(sf.getProperty(machinePropName)) flatMap {
          p: Property =>
            p.getValue.toString match {
              // Apparently ogr2ogr stores bools as ints /shrug
              case "1" => Some(true)
              case "0" => Some(false)
              case _   => None
            }
        }

        val confPropName = fields.getOrElse("confidence", null)
        val conf = confPropName match {
          case null => None
          case _ =>
            Option(sf.getProperty(confPropName)) flatMap { p: Property =>
              decode[Float](p.getValue.toString).toOption
            }
        }

        val quaPropName = fields.getOrElse("quality", null)
        val qua = quaPropName match {
          case null => None
          case _ =>
            Option(sf.getProperty(quaPropName)) flatMap { p: Property =>
              decode[AnnotationQuality](p.getValue.toString).toOption
            }
        }
        (isM, conf, qua)
    }

    // annotationGroup is passed None in here since it will be handled
    // in insertAnnotations in AnnotationDao
    Some(
      Create(
        Some(userId),
        label,
        description,
        isMachine,
        confidence,
        quality,
        Some(projected),
        None
      )
    )
  }

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
        properties.verifiedBy
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
                     defaultAnnotationGroup: UUID): Annotation = {
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
        verifiedBy
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
