package com.azavea.rf.export

import com.azavea.rf.export.model._
import com.azavea.rf.export.util._

import geotrellis.proj4.LatLng
import geotrellis.raster._
import geotrellis.raster.io.geotiff.{GeoTiff, MultibandGeoTiff}
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.tiling._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.file._
import geotrellis.spark.io.s3._

import com.typesafe.scalalogging.LazyLogging
import org.apache.hadoop.fs.Path
import org.apache.spark._
import spray.json._

import java.util.UUID


object Export extends SparkJob with LazyLogging {
  /** Get a LayerReader and an attribute store for the catalog located at the provided URI
    *
    * @param exportLayerDef export job's layer definition
    */
  def getRfLayerManagement(exportLayerDef: ExportLayerDefinition)(implicit @transient sc: SparkContext): (FilteringLayerReader[LayerId], AttributeStore) =
    exportLayerDef.ingestLocation.getScheme match {
      case "s3" | "s3a" | "s3n" =>
        val (bucket, prefix) = S3.parse(exportLayerDef.ingestLocation)
        val reader = S3LayerReader(bucket, prefix)
        (reader, reader.attributeStore)
      case "file" =>
        val reader = FileLayerReader(exportLayerDef.ingestLocation.getPath)
        (reader, reader.attributeStore)
    }

  def exportProject(params: CommandLine.Params)(exportDefinition: ExportDefinition)(implicit @transient sc: SparkContext) = {
    val wrappedConfiguration = HadoopConfiguration(S3.setCredentials(sc.hadoopConfiguration))
    val (input, output) = exportDefinition.input -> exportDefinition.output
    val rdds = input.layers.map { ld =>
      val (reader, attributeStore) = getRfLayerManagement(ld)
      val layerId = LayerId(ld.layerId.toString, input.resolution)
      val md = attributeStore.readMetadata[TileLayerMetadata[SpatialKey]](layerId)
      val crs = output.crs.getOrElse(md.crs)

      val query = {
        val q = reader.query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](layerId)
        input.mask.fold(q)(mp => q.where(Intersects(mp.reproject(LatLng, md.crs))))
      } result

      val bandsQuery = query.withContext {
        _.mapValues { tile =>
          output.render.flatMap(_.bands.map(_.toSeq)) match {
            case Some(seq) if seq.nonEmpty => tile.subsetBands(seq)
            case _ => tile
          }
        }
      }

      output.rasterSize match {
        case Some(rs) =>
          bandsQuery.reproject(ZoomedLayoutScheme(crs, rs))._2
        case _ =>
          bandsQuery.reproject(ZoomedLayoutScheme(crs))._2
      }
    }

    if(!output.stitch) {
      val result =
        rdds
          .map { rdd =>
            val md = rdd.metadata
            rdd.map { case (key, tile) =>
              (key, GeoTiff(tile, md.mapTransform(key), md.crs))
            }
          }
          .reduce(_ union _)
          .combineByKey(createTiles[MultibandGeoTiff], mergeTiles1[MultibandGeoTiff], mergeTiles2[MultibandGeoTiff])
          .mapValues { seq =>
            val head = seq.head
            GeoTiff(seq.map(_.tile).reduce(_ merge _), head.extent, head.crs)
          }

      result.foreachPartition { iter =>
        val configuration = wrappedConfiguration.get
        iter.foreach { case (key, tile) =>
          tile.write(
            new Path(s"${output.source.toString}/${input.resolution}-${key.col}-${key.row}.tiff"),
            configuration
          )
        }
      }
    } else {
      val md = rdds.map(_.metadata).reduce(_ combine _)
      val tile =
        ContextRDD(
          rdds
            .reduce((f, s) => f.withContext { _.union(s) })
            .combineByKey(createTiles[MultibandTile], mergeTiles1[MultibandTile], mergeTiles2[MultibandTile])
            .mapValues { _.reduce(_ merge _) }, md
        ).stitch
      val raster =
        if(output.crop) input.mask.fold(tile)(mp => tile.crop(mp.envelope.reproject(LatLng, md.crs)))
        else tile

      GeoTiff(raster, md.crs).write(
        new Path(s"${output.source.toString}/${input.resolution}-${UUID.randomUUID()}.tiff"),
        wrappedConfiguration.get
      )
    }
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

    val exportDefinition = readString(params.jobDefinition).parseJson.convertTo[ExportDefinition]

    implicit val sc = new SparkContext(conf)
    try {
      exportProject(params)(exportDefinition)
    } finally {
      sc.stop
    }
  }
}
