package com.azavea.rf.batch.export.spark

import java.util.UUID

import scala.concurrent.ExecutionContext.Implicits.global

import com.azavea.rf.batch._
import com.azavea.rf.batch.ast._
import com.azavea.rf.batch.dropbox._
import com.azavea.rf.batch.export._
import com.azavea.rf.batch.util.conf._
import com.azavea.rf.datamodel._

import _root_.io.circe.parser._
import cats.data.Validated._
import cats.implicits._
import com.azavea.rf.batch.util._
import com.azavea.rf.database.Database
import com.azavea.rf.datamodel._
import com.dropbox.core.v2.files.{CreateFolderErrorException, WriteMode}
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4.CRS
import geotrellis.proj4.{CRS, LatLng}
import geotrellis.raster._
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.io._
import geotrellis.raster.io.geotiff.{GeoTiff, SinglebandGeoTiff}
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.tiling.MapKeyTransform
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.spark._
import org.apache.spark.rdd.RDD
import spray.json.DefaultJsonProtocol._

// --- //

object Export extends SparkJob with Config with LazyLogging {

  val jobName = "Export"

  implicit lazy val database = Database.DEFAULT

  def exportProject(ed: ExportDefinition)(implicit @transient sc: SparkContext): Unit = {

    val wrappedConfiguration: HadoopConfiguration =
      HadoopConfiguration(S3.setCredentials(sc.hadoopConfiguration))

    interpretRDD(ed.input.ast, ed.input.params.sources, ed.input.resolution) match {
      case Invalid(errs) => ???
      case Valid(rdd) => {

        val mt: MapKeyTransform = rdd.metadata.layout.mapTransform
        val crs: CRS = rdd.metadata.crs

        if(!ed.output.stitch) {
          /* Create GeoTiffs and output them */
          val singles: RDD[(SpatialKey, SinglebandGeoTiff)] =
            rdd.map({ case (key, tile) => (key, SinglebandGeoTiff(tile, mt(key), crs)) })

          writeGeoTiffs(singles, ed, wrappedConfiguration)
        } else {
          /* Stitch the Layer into a single GeoTiff and output it */
          val single: SinglebandGeoTiff = GeoTiff(rdd.stitch, crs)

          single.write(
            new Path(s"${ed.output.source.toString}/${ed.input.resolution}-${UUID.randomUUID()}.tiff"),
            wrappedConfiguration.get
          )
        }
      }
    }
  }

  /** Write a layer of GeoTiffs. */
  def writeGeoTiffs(
    rdd: RDD[(SpatialKey, SinglebandGeoTiff)],
    ed: ExportDefinition,
    conf: HadoopConfiguration
  ): Unit = {
    rdd.foreachPartition({ iter =>
      // TODO: Is this thread safe? This type seems to be mutable.
      val config: Configuration = conf.get

      iter.foreach({ case (key, tile) =>
        tile.write(
          new Path(s"${ed.output.source.toString}/${ed.input.resolution}-${key.col}-${key.row}.tiff"),
          config
        )
      })
    })
  }

  /**
    *  Sample ingest definitions can be found in the accompanying test/resources
    *
    * @param args Arguments to be parsed by the tooling defined in [[CommandLine]]
    */
  def main(args: Array[String]): Unit = {
    val params = CommandLine.parser.parse(args, CommandLine.Params()) match {
      case Some(params) =>
        params
      case None =>
        throw new Exception("Unable to parse command line arguments")
    }

    val exportDef =
      decode[ExportDefinition](readString(params.jobDefinition)) match {
        case Right(r) => r
        case _ => throw new Exception("Incorrect ExportDefinition JSON")
      }

    implicit val sc = new SparkContext(conf)

    try {
      exportProject(exportDef)
    } finally {
      sc.stop
    }
  }
}
