package com.rasterfoundry.api.project

import better.files._
import java.util.Calendar

import com.rasterfoundry.datamodel.{Annotation, User}
import com.rasterfoundry.api.utils.Config
import com.typesafe.scalalogging.LazyLogging
import com.vividsolutions.jts.{geom => jts}
import com.amazonaws.services.s3.AmazonS3URI
import com.rasterfoundry.common.S3
import geotrellis.geotools._
import geotrellis.proj4.CRS
import geotrellis.vector._
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
import org.geotools.referencing.{CRS => geotoolsCRS}
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.opengis.feature.simple.SimpleFeature
import org.opengis.referencing.crs.CoordinateReferenceSystem

import java.util.{HashMap => JHashMap}

object AnnotationShapefileService extends LazyLogging with Config {

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

  def getAnnotationShapefileDownloadUrl(annotations: List[Annotation], user: User): String = {
    val zipfile: File = annotationsToShapefile(annotations)
    val cal: Calendar = Calendar.getInstance()
    val s3Client = S3()
    val s3Uri: AmazonS3URI = new AmazonS3URI(
      user.getDefaultAnnotationShapefileSource(dataBucket))

    cal.add(Calendar.DAY_OF_YEAR, 1)
    s3Client
      .putObject(dataBucket, s3Uri.getKey, zipfile.toJava)
      .setExpirationTime(cal.getTime)
    zipfile.delete(true)
    s3Client.getSignedUrl(dataBucket, s3Uri.getKey).toString()
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
          ("label", annotation.label match {
            case "" => "Unlabeled"
            case _  => annotation.label
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
