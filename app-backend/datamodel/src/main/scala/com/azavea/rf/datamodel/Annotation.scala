package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.{UUID, HashMap => JHashMap}

import better.files._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import com.vividsolutions.jts.{geom => jts}
import geotrellis.geotools._
import geotrellis.proj4.{CRS, WebMercator}
import io.circe.generic.JsonCodec
import io.circe.generic.extras._
import io.circe.parser._
import geotrellis.vector.{Geometry, Projected, _}
import geotrellis.vector.reproject.Reproject
import org.geotools.data.DefaultTransaction
import org.geotools.data.shapefile.{
  ShapefileDataStore,
  ShapefileDataStoreFactory
}
import org.geotools.data.simple.SimpleFeatureStore
import org.geotools.feature.DefaultFeatureCollection
import org.geotools.feature.simple.{
  SimpleFeatureBuilder,
  SimpleFeatureTypeBuilder
}
import org.geotools.referencing.crs.DefaultGeographicCRS
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
        properties.label.length match {
          case 0 => "Unlabeled"
          case _ => properties.label
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
        label.length match {
          case 0 => "Unlabeled"
          case _ => label
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

object AnnotationShapefileService extends LazyLogging {

  def annotationsToShapefile(annotations: Seq[Annotation]): File = {

    val annotationFeatures = annotations.flatMap(this.createSimpleFeature)

    val featureCollection = new DefaultFeatureCollection()
    annotationFeatures.foreach(feature => {
      val x = featureCollection.add(feature)
    })

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
        // annotations in RF DB are projected to EPSG: 3857, WebMercator
        // when exporting, we reproject them to EPSG:4326, WGS:84
        val geom = Reproject(
          geometry.geom,
          CRS.fromEpsgCode(3857),
          CRS.fromEpsgCode(4326)
        )
        val geometryField = "the_geom"
        val sftb = (new SimpleFeatureTypeBuilder)
          .minOccurs(1)
          .maxOccurs(1)
          .nillable(false)

        sftb.setName("Annotaion")
        geom match {
          case pt: Point   => sftb.add(geometryField, classOf[jts.Point])
          case ln: Line    => sftb.add(geometryField, classOf[jts.LineString])
          case pg: Polygon => sftb.add(geometryField, classOf[jts.Polygon])
          case mp: MultiPoint =>
            sftb.add(geometryField, classOf[jts.MultiPoint])
          case ml: MultiLine =>
            sftb.add(geometryField, classOf[jts.MultiLineString])
          case mp: MultiPolygon =>
            sftb.add(geometryField, classOf[jts.MultiPolygon])
          case g: Geometry =>
            throw new Exception(s"Unhandled Geotrellis Geometry $g")
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

        data.foreach({
          case (key, value) =>
            sftb
              .minOccurs(1)
              .maxOccurs(1)
              .nillable(false)
              .add(key, value.getClass)
        })

        val sft = sftb.buildFeatureType
        val sfb = new SimpleFeatureBuilder(sft)

        geom match {
          case Point(pt)        => sfb.add(pt)
          case Line(ln)         => sfb.add(ln)
          case Polygon(pg)      => sfb.add(pg)
          case MultiPoint(mp)   => sfb.add(mp)
          case MultiLine(ml)    => sfb.add(ml)
          case MultiPolygon(mp) => sfb.add(mp)
          case g: Geometry =>
            throw new Exception(s"Unhandled Geotrellis Geometry $g")
        }
        data.foreach({ case (key, value) => sfb.add(value) })

        Some(sfb.buildFeature(annotation.id.toString))
      case _ =>
        None
    }
  }

  @SuppressWarnings(Array("AsInstanceOf", "CatchThrowable"))
  def createShapefiles(featureCollection: DefaultFeatureCollection,
                       directory: File): Unit = {
    val shapeFile = directory / "shapefile.shp"

    val dataStoreFactory = new ShapefileDataStoreFactory()

    val params = new JHashMap[String, java.io.Serializable]()
    params.put("url", shapeFile.url)
    params.put("create spatial index", true)

    val newDataStore = dataStoreFactory
      .createNewDataStore(params)
      .asInstanceOf[ShapefileDataStore]
    newDataStore.createSchema(featureCollection.getSchema)
    // we reprojected annotations from WebMercator to WGS84 above
    // so schema should be as follow
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
            throw default
        } finally {
          transaction.close()
        }
    }
  }
}
