package com.azavea.rf.datamodel

import java.util.{UUID, Map => JMap, HashMap => JHashMap}
import java.sql.Timestamp

import scala.collection.JavaConverters._

import better.files._
import cats.implicits._
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.extras._
import io.circe.parser._

import geotrellis.slick.Projected
import geotrellis.vector.Geometry
import geotrellis.vector._
import geotrellis.vector.io.wkt.WKT
import geotrellis.vector.reproject.Reproject
import geotrellis.proj4.CRS
import geotrellis.geotools._
import geotrellis.proj4.{LatLng, WebMercator}

import com.vividsolutions.jts.{geom => jts}

import org.geotools.data.{DataUtilities, DefaultTransaction}
import org.geotools.data.shapefile.{ShapefileDataStore, ShapefileDataStoreFactory}
import org.geotools.data.simple.{SimpleFeatureCollection, SimpleFeatureStore}
import org.geotools.feature.{DefaultFeatureCollection, DefaultFeatureCollections}
import org.geotools.feature.simple.{SimpleFeatureTypeBuilder, SimpleFeatureBuilder}
import org.opengis.feature.Property
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
  label: String,
  description: Option[String],
  machineGenerated: Option[Boolean],
  confidence: Option[Float],
  quality: Option[AnnotationQuality],
  geometry: Option[Projected[Geometry]],
  annotationGroup: UUID
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
        this.label,
        this.description,
        this.machineGenerated,
        this.confidence,
        this.quality,
        this.annotationGroup
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
  label: String,
  description: Option[String],
  machineGenerated: Option[Boolean],
  confidence: Option[Float],
  quality: Option[AnnotationQuality],
  annotationGroup: UUID
)

@JsonCodec
case class AnnotationPropertiesCreate(
  owner: Option[String],
  label: String,
  description: Option[String],
  machineGenerated: Option[Boolean],
  confidence: Option[Float],
  quality: Option[AnnotationQuality],
  annotationGroup: Option[UUID]
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


  def fromSimpleFeature(sf: SimpleFeature): Option[Create] = {
    println("fromSimpleFeature")
    val geom = WKT.read(sf.getDefaultGeometry.toString)
    println("geom")
    println(geom)
    val projected = Projected(Reproject(geom, LatLng, WebMercator), 3857)
    println("projected")
    println(projected)
    println("projected.isValid")
    println(projected.isValid)
    println("sf.getProperties()")
    println(sf.getProperties())

    val owner = Option(sf.getAttribute("owner")) map { (o: Object) => o.toString }
    val label = sf.getProperty("label").getValue.toString
    val description = Option(sf.getAttribute("descriptio")) map { (o: Object) => o.toString }
    val machineGenerated = Option(sf.getProperty("machineGen")) flatMap {
      (p: Property) => p.getValue.toString match {
        // Apparently ogr2ogr stores bools as ints /shrug
        case "1" => Some(true)
        case "0" => Some(false)
        case _ => None
      }
    }
    val confidence = Option(sf.getProperty("confidence")) flatMap {
      (p: Property) =>  decode[Float](p.getValue.toString).toOption
    }
    val quality = Option(sf.getProperty("quality")) flatMap {
      (p: Property) => decode[AnnotationQuality](p.getValue.toString).toOption
    }
    // TODO: this is not correct
    // this function is only used when uploading shapefile to a project
    // so if a project has annotations, it should use the same group id
    // if not, create one
    val annotationGroup = Option(sf.getProperty("group")) flatMap {
      (p: Property) => decode[UUID](p.getValue.toString).toOption
    }
    Some(
      Create(
        Some("auth0|59318a9d2fbbca3e16bcfc92"),
        label,
        description,
        machineGenerated,
        confidence,
        quality,
        Some(projected),
        annotationGroup
      )
    )
    // if (projected.isValid) {
    //   val owner = Option(sf.getAttribute("owner")) map { (o: Object) => o.toString }
    //   val label = sf.getProperty("label").getValue.toString
    //   val description = Option(sf.getAttribute("descriptio")) map { (o: Object) => o.toString }
    //   val machineGenerated = Option(sf.getProperty("machineGen")) flatMap {
    //     (p: Property) => p.getValue.toString match {
    //       // Apparently ogr2ogr stores bools as ints /shrug
    //       case "1" => Some(true)
    //       case "0" => Some(false)
    //       case _ => None
    //     }
    //   }
    //   val confidence = Option(sf.getProperty("confidence")) flatMap {
    //     (p: Property) =>  decode[Float](p.getValue.toString).toOption
    //   }
    //   val quality = Option(sf.getProperty("quality")) flatMap {
    //     (p: Property) => decode[AnnotationQuality](p.getValue.toString).toOption
    //   }
    //   // TODO: this is not correct
    //   // this function is only used when uploading shapefile to a project
    //   // so if a project has annotations, it should use the same group id
    //   // if not, create one
    //   val annotationGroup = Option(sf.getProperty("group")) flatMap {
    //     (p: Property) => decode[UUID](p.getValue.toString).toOption
    //   }
    //   Some(
    //     Create(
    //       Some("auth0|59318a9d2fbbca3e16bcfc92"),
    //       label,
    //       description,
    //       machineGenerated,
    //       confidence,
    //       quality,
    //       Some(projected),
    //       annotationGroup
    //     )
    //   )
    // } else {
    //   None
    // }
  }

  @ConfiguredJsonCodec
  case class GeoJSON(
    id: UUID,
    geometry: Option[Projected[Geometry]],
    properties: AnnotationProperties,
    _type: String = "Feature"
  ) extends GeoJSONFeature {
    def toAnnotation: Annotation = {
      Annotation(
        id,
        properties.projectId,
        properties.createdAt,
        properties.createdBy,
        properties.modifiedAt,
        properties.modifiedBy,
        properties.owner,
        properties.label.length match {
          case 0 => "Unlabeled"
          case _ => properties.label
        },
        properties.description,
        properties.machineGenerated,
        properties.confidence,
        properties.quality,
        geometry,
        properties.annotationGroup
      )
    }
  }

  @JsonCodec
  case class Create(
    owner: Option[String],
    label: String,
    description: Option[String],
    machineGenerated: Option[Boolean],
    confidence: Option[Float],
    quality: Option[AnnotationQuality],
    geometry: Option[Projected[Geometry]],
    annotationGroup: Option[UUID]
  ) extends OwnerCheck {

    def toAnnotation(projectId: UUID, user: User, defaultAnnotationGroup: UUID): Annotation = {
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
        label.length match {
          case 0 => "Unlabeled"
          case _ => label
        },
        description,
        machineGenerated,
        confidence,
        quality,
        geometry,
        annotationGroup.getOrElse(defaultAnnotationGroup)
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
        geometry,
        properties.annotationGroup
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
          ("label", annotation.label.length match {
            case 0 => "Unlabeled"
            case _ => annotation.label
          }),
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
          case default: Throwable => {
            transaction.rollback()
            throw default
          }
        } finally {
          transaction.close()
        }
    }
  }
}
