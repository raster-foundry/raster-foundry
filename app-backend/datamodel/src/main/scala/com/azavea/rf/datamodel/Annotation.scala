package com.azavea.rf.datamodel

import java.util.{UUID, Map => JMap, HashMap => JHashMap}
import java.sql.Timestamp

import scala.collection.JavaConverters._

import better.files._
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.extras._

import geotrellis.slick.Projected
import geotrellis.vector.Geometry
import geotrellis.vector._
import geotrellis.proj4.CRS
import geotrellis.geotools._
import geotrellis.proj4.{LatLng, WebMercator}

import com.vividsolutions.jts.{geom => jts}

import org.geotools.data.{DataUtilities, DefaultTransaction}
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}
import org.geotools.data.simple.{SimpleFeatureCollection, SimpleFeatureStore}
import org.geotools.feature.{DefaultFeatureCollection, DefaultFeatureCollections}
import org.geotools.feature.simple.{SimpleFeatureTypeBuilder, SimpleFeatureBuilder}
import org.opengis.feature.simple.{SimpleFeature, SimpleFeatureType}
import org.geotools.referencing.crs.DefaultGeographicCRS

import com.typesafe.scalalogging.LazyLogging

@JsonCodec
case class Annotation(
  id: UUID,
  projectId: UUID,
  createdAt: Timestamp,
  createdBy: String,
  modifiedAt: Timestamp,
  modifiedBy: String,
  owner: String,
  organizationId: UUID,
  label: String,
  description: Option[String],
  machineGenerated: Option[Boolean],
  confidence: Option[Float],
  quality: Option[AnnotationQuality],
  geometry: Option[Projected[Geometry]]
) extends GeoJSONSerializable[Annotation.GeoJSON] {
    def toGeoJSONFeature: Annotation.GeoJSON = {
        Annotation.GeoJSON(
            this.id,
            this.geometry,
            AnnotationProperties(
                this.projectId,
                this.createdAt,
                this.createdBy,
                this.modifiedAt,
                this.modifiedBy,
                this.owner,
                this.organizationId,
                this.label,
                this.description,
                this.machineGenerated,
                this.confidence,
                this.quality
            ),
            "Feature"
        )
    }
}

@JsonCodec
case class AnnotationProperties(
    projectId: UUID,
    createdAt: Timestamp,
    createdBy: String,
    modifiedAt: Timestamp,
    modifiedBy: String,
    owner: String,
    organizationId: UUID,
    label: String,
    description: Option[String],
    machineGenerated: Option[Boolean],
    confidence: Option[Float],
    quality: Option[AnnotationQuality]
)

@JsonCodec
case class AnnotationPropertiesCreate(
    owner: Option[String],
    label: String,
    description: Option[String],
    machineGenerated: Option[Boolean],
    confidence: Option[Float],
    quality: Option[AnnotationQuality]
)


object Annotation {

    implicit val config: Configuration = Configuration.default.copy(
      transformMemberNames = {
        case "_type" => "type"
        case other => other
      }
    )

    def tupled = (Annotation.apply _).tupled
    def create = Create.apply _


    @ConfiguredJsonCodec
    case class GeoJSON(
        id: UUID,
        geometry: Option[Projected[Geometry]],
        properties: AnnotationProperties,
        _type: String = "Feature"
    ) extends GeoJSONFeature

    @JsonCodec
    case class Create(
        owner: Option[String],
        label: String,
        description: Option[String],
        machineGenerated: Option[Boolean],
        confidence: Option[Float],
        quality: Option[AnnotationQuality],
        geometry: Option[Projected[Geometry]]
    ) extends OwnerCheck {

        def toAnnotation(projectId: UUID, user: User): Annotation = {
            val now = new Timestamp((new java.util.Date()).getTime())
            val ownerId = checkOwner(user, this.owner)
            Annotation(
                UUID.randomUUID, // id
                projectId, // projectId
                now, // createdAt
                user.id, // createdBy
                now, // modifiedAt
                user.id, // modifiedBy
                ownerId, // owner
                user.organizationId,
                label,
                description,
                machineGenerated,
                confidence,
                quality,
                geometry
            )
        }
    }

    @JsonCodec
    case class GeoJSONFeatureCreate(
        geometry: Option[Projected[Geometry]],
        properties: AnnotationPropertiesCreate
    ) extends OwnerCheck {
        def toAnnotationCreate(): Annotation.Create = {
            Annotation.Create(
                properties.owner,
                properties.label,
                properties.description,
                properties.machineGenerated,
                properties.confidence,
                properties.quality,
                geometry
            )
        }
    }
}

object AnnotationShapefileService extends LazyLogging {

  def annotationsToShapefile(annotations: Seq[Annotation]): File = {

    val annotationFeatures = annotations.map(this.createSimpleFeature).flatten

    val featureCollection = new DefaultFeatureCollection()
    annotationFeatures.foreach(
      feature => {
        val x = featureCollection.add(feature)
      }
    )

    val zipfile = File.newTemporaryFile("export", ".zip")

    File.usingTemporaryDirectory() { directory =>
      this.createShapefiles(featureCollection, directory)
      directory.zipTo(destination = zipfile)
    }
    zipfile
  }

  // TODO: Update this to use GeoTrellis's build in conversion once the id bug is fixed:
  //       https://github.com/locationtech/geotrellis/issues/2575
  def createSimpleFeature(annotation: Annotation): Option[SimpleFeature] = {
    annotation.geometry match {
      case Some(geometry) =>
        val geom = geometry.geom
        val geometryField = "the_geom"
        val sftb = (new SimpleFeatureTypeBuilder).minOccurs(1).maxOccurs(1).nillable(false)

        sftb.setName("Annotaion")
        geom match {
          case pt: Point => sftb.add(geometryField, classOf[jts.Point])
          case ln: Line => sftb.add(geometryField, classOf[jts.LineString])
          case pg: Polygon => sftb.add(geometryField, classOf[jts.Polygon])
          case mp: MultiPoint => sftb.add(geometryField, classOf[jts.MultiPoint])
          case ml: MultiLine => sftb.add(geometryField, classOf[jts.MultiLineString])
          case mp: MultiPolygon => sftb.add(geometryField, classOf[jts.MultiPolygon])
          case  g: Geometry => throw new Exception(s"Unhandled Geotrellis Geometry $g")
        }
        sftb.setDefaultGeometry(geometryField)

        val data = Seq(
          ("id", annotation.id),
          ("label", annotation.label),
          ("desc", annotation.description.getOrElse("")),
          ("machinegen", annotation.machineGenerated.getOrElse(false)),
          ("confidence", annotation.confidence.getOrElse(0)),
          ("quality", annotation.quality.getOrElse("UNSURE").toString)
        )

        data.foreach({ case (key, value) => sftb
                          .minOccurs(1).maxOccurs(1).nillable(false)
                          .add(key, value.getClass)
                     })

        val sft = sftb.buildFeatureType
        val sfb = new SimpleFeatureBuilder(sft)

        geom match {
          case Point(pt) => sfb.add(pt)
          case Line(ln) => sfb.add(ln)
          case Polygon(pg) => sfb.add(pg)
          case MultiPoint(mp) => sfb.add(mp)
          case MultiLine(ml) => sfb.add(ml)
          case MultiPolygon(mp) => sfb.add(mp)
          case g: Geometry => throw new Exception(s"Unhandled Geotrellis Geometry $g")
        }
        data.foreach({ case (key, value) => sfb.add(value) })

        Some(sfb.buildFeature(annotation.id.toString))
      case _ =>
        None
    }
  }

  def createShapefiles(featureCollection: DefaultFeatureCollection, directory: File) = {
    val shapeFile = directory / "shapefile.shp"

    val dataStoreFactory = new ShapefileDataStoreFactory()

    val params = new JHashMap[String, java.io.Serializable]()
    params.put("url", shapeFile.url)
    params.put("create spatial index", true)

    val newDataStore = dataStoreFactory
      .createNewDataStore(params)
      .asInstanceOf[ShapefileDataStore]
    newDataStore.createSchema(featureCollection.getSchema)
    newDataStore.forceSchemaCRS(DefaultGeographicCRS.WGS84)

    val transaction = new DefaultTransaction("create")
    val typeName = newDataStore.getTypeNames.head

    newDataStore.getFeatureSource(typeName) match {
      case featureStore: SimpleFeatureStore =>
        featureStore.setTransaction(transaction)
        try {
          featureStore.addFeatures(featureCollection)
          transaction.commit()
        } catch {
          case default: Throwable =>
            transaction.rollback()
        } finally {
          transaction.close()
        }
    }
  }
}
