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
import com.azavea.rf.tool.ast.MapAlgebraAST
import com.azavea.rf.tool.params.EvalParams
import com.dropbox.core.v2.files.{CreateFolderErrorException, WriteMode}
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4.{CRS, LatLng}
import geotrellis.raster._
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.io._
import geotrellis.raster.io.geotiff.{GeoTiff, MultibandGeoTiff, SinglebandGeoTiff}
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.s3._
import geotrellis.spark.tiling.MapKeyTransform
import geotrellis.spark.tiling._
import geotrellis.vector.MultiPolygon
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.spark._
import org.apache.spark.rdd.RDD
import spray.json.DefaultJsonProtocol._

// --- //

object Export extends SparkJob with Config with LazyLogging {

  val jobName = "Export"

  implicit lazy val database = Database.DEFAULT

  def astExport(
    ed: ExportDefinition,
    ast: MapAlgebraAST,
    params: EvalParams,
    conf: HadoopConfiguration
  )(implicit sc: SparkContext): Unit = {
    interpretRDD(ast, params.sources, ed.input.resolution) match {
      case Invalid(errs) => ???
      case Valid(rdd) => {

        val mt: MapKeyTransform = rdd.metadata.layout.mapTransform
        val crs: CRS = rdd.metadata.crs

        if(!ed.output.stitch) {
          /* Create GeoTiffs and output them */
          val singles: RDD[(SpatialKey, SinglebandGeoTiff)] =
            rdd.map({ case (key, tile) => (key, SinglebandGeoTiff(tile, mt(key), crs)) })

          writeGeoTiffs[Tile, SinglebandGeoTiff](singles, ed, conf)
        } else {
          /* Stitch the Layer into a single GeoTiff and output it */
          val single: SinglebandGeoTiff = GeoTiff(rdd.stitch, crs)

          single.write(
            new Path(s"${ed.output.source.toString}/${ed.input.resolution}-${UUID.randomUUID()}.tiff"),
            conf.get
          )
        }
      }
    }
  }

  /** Get a LayerReader and an attribute store for the catalog located at the provided URI
    *
    *  @param exportLayerDef export job's layer definition
    */
  def getRfLayerManagement(
    ed: ExportLayerDefinition
  )(implicit @transient sc: SparkContext): (FilteringLayerReader[LayerId], AttributeStore) = {
    ed.ingestLocation.getScheme match {
      case "s3" | "s3a" | "s3n" =>
        val (bucket, prefix) = S3.parse(ed.ingestLocation)
        val reader = S3LayerReader(bucket, prefix)
        (reader, reader.attributeStore)
      case "file" =>
        val reader = FileLayerReader(ed.ingestLocation.getPath)
        (reader, reader.attributeStore)
    }
  }

  def multibandExport(
    ed: ExportDefinition,
    layers: Array[ExportLayerDefinition],
    mask: Option[MultiPolygon],
    conf: HadoopConfiguration
  )(implicit @transient sc: SparkContext): Unit = {

    val rdds = layers.map { ld =>
      val (reader, store) = getRfLayerManagement(ld)
      val layerId = LayerId(ld.layerId.toString, ed.input.resolution)
      val md = store.readMetadata[TileLayerMetadata[SpatialKey]](layerId)
      val hist = ld.colorCorrections.map { _ => store.read[Array[Histogram[Double]]](LayerId(layerId.name, 0), "histogram") }
      val crs = ed.output.crs.getOrElse(md.crs)

      val q = reader.query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](layerId)

      val query: ContextRDD[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]] =
        mask
          .fold(q)(mp => q.where(Intersects(mp.reproject(LatLng, md.crs))))
          .result
          .withContext({ rdd => rdd.mapValues({ tile =>
            val ctile = (ld.colorCorrections |@| hist) map { _.colorCorrect(tile, _) } getOrElse tile

            ed.output.render.flatMap(_.bands.map(_.toSeq)) match {
              case Some(seq) if seq.nonEmpty => ctile.subsetBands(seq)
              case _ => ctile
            }
          })})

      ed.output.rasterSize match {
        case Some(rs) if md.crs != crs || rs != md.tileCols || rs != md.tileRows =>
          query.reproject(ZoomedLayoutScheme(crs, rs))._2
        case _ if md.crs != crs =>
          query.reproject(ZoomedLayoutScheme(crs, math.min(md.tileCols, md.tileRows)))._2
        case _ => query
      }
    }

    /** Tile merge with respect to layer initial ordering */
    if(!ed.output.stitch) {
      val result: RDD[(SpatialKey, MultibandGeoTiff)] =
        rdds
          .zipWithIndex
          .map { case (rdd, i) =>
            val md = rdd.metadata
            rdd.map { case (key, tile) => (key, i -> GeoTiff(tile, md.mapTransform(key), md.crs)) }
          }
          .reduce(_ union _)
          .combineByKey(createTiles[(Int, MultibandGeoTiff)], mergeTiles1[(Int, MultibandGeoTiff)], mergeTiles2[(Int, MultibandGeoTiff)])
          .mapValues { seq =>
            val sorted = seq.sortBy(_._1).map(_._2)
            sorted.headOption map { head =>
              GeoTiff(sorted.map(_.tile).reduce(_ merge _), head.extent, head.crs)
            }
          }
          .flatMapValues(v => v)

      writeGeoTiffs[MultibandTile, MultibandGeoTiff](result, ed, conf)
    } else {
      val md = rdds.map(_.metadata).reduce(_ combine _)
      val raster: Raster[MultibandTile] =
        rdds
          .zipWithIndex
          .map { case (rdd, i) => rdd.mapValues { value => i -> value } }
          .foldLeft(ContextRDD(sc.emptyRDD[(SpatialKey, (Int, MultibandTile))], md))((acc, r) => acc.withContext { _.union(r) })
          .withContext { _.combineByKey(createTiles[(Int, MultibandTile)], mergeTiles1[(Int, MultibandTile)], mergeTiles2[(Int, MultibandTile)]) }
          .withContext { _.mapValues { _.sortBy(_._1).map(_._2).reduce(_ merge _) } }
          .stitch
      val craster =
        if(ed.output.crop) mask.fold(raster)(mp => raster.crop(mp.envelope.reproject(LatLng, md.crs)))
        else raster

      GeoTiff(craster, md.crs).write(
        new Path(s"${ed.output.source.toString}/${ed.input.resolution}-${UUID.randomUUID()}.tiff"),
        conf.get
      )
    }
  }

  /** Write a layer of GeoTiffs. */
  def writeGeoTiffs[T <: CellGrid, G <: GeoTiff[T]](
    rdd: RDD[(SpatialKey, G)],
    ed: ExportDefinition,
    conf: HadoopConfiguration
  ): Unit = {
    rdd.foreachPartition({ iter =>
      // TODO: Is this thread safe? This type seems to be mutable.
      val config: Configuration = conf.get

      iter.foreach({ case (key, tile) =>
        tile.write(
          new Path(s"${ed.output.source.toString}/${ed.input.resolution}-${key.col}-ed.${key.row}.tiff"),
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
      val conf: HadoopConfiguration =
        HadoopConfiguration(S3.setCredentials(sc.hadoopConfiguration))

      exportDef.input.style match {
        case Left(SimpleInput(layers, mask)) => multibandExport(exportDef, layers, mask, conf)
        case Right(ASTInput(ast, params)) => astExport(exportDef, ast, params, conf)
      }
    } finally {
      sc.stop
    }
  }
}
