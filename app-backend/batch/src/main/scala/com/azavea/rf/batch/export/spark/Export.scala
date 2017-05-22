package com.azavea.rf.batch.export.spark

import io.circe.parser._
import com.azavea.rf.datamodel._
import com.azavea.rf.batch._
import com.azavea.rf.batch.util._
import com.azavea.rf.batch.export._
import com.azavea.rf.batch.dropbox._
import com.azavea.rf.batch.util.conf._

import geotrellis.proj4.LatLng
import geotrellis.raster._
import geotrellis.raster.io._
import geotrellis.raster.histogram.Histogram
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
import spray.json.DefaultJsonProtocol._
import cats.implicits._
import com.dropbox.core.v2.files.{CreateFolderErrorException, WriteMode}

import java.util.UUID

object Export extends SparkJob with Config with LazyLogging {
  val jobName = "Export"

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
      val hist = ld.colorCorrections.map { _ => attributeStore.read[Array[Histogram[Double]]](LayerId(layerId.name, 0), "histogram") }
      val crs = output.crs.getOrElse(md.crs)

      val query = {
        val q = reader.query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](layerId)
        input.mask.fold(q)(mp => q.where(Intersects(mp.reproject(LatLng, md.crs))))
      } result

      val bandsQuery = query.withContext {
        _.mapValues { tile =>
          val ctile = (ld.colorCorrections |@| hist) map { _.colorCorrect(tile, _) } getOrElse tile

          output.render.flatMap(_.bands.map(_.toSeq)) match {
            case Some(seq) if seq.nonEmpty => ctile.subsetBands(seq)
            case _ => ctile
          }
        }
      }

      output.rasterSize match {
        case Some(rs) if md.crs != crs || rs != md.tileCols || rs != md.tileRows =>
          bandsQuery.reproject(ZoomedLayoutScheme(crs, rs))._2
        case _ if md.crs != crs =>
          bandsQuery.reproject(ZoomedLayoutScheme(crs, math.min(md.tileCols, md.tileRows)))._2
        case _ => bandsQuery
      }
    }

    /** Tile merge with respect to layer initial ordering */
    if(!output.stitch) {
      val result =
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
      val raster =
        rdds
          .zipWithIndex
          .map { case (rdd, i) => rdd.mapValues { value => i -> value } }
          .foldLeft(ContextRDD(sc.emptyRDD[(SpatialKey, (Int, MultibandTile))], md))((acc, r) => acc.withContext { _.union(r) })
          .withContext { _.combineByKey(createTiles[(Int, MultibandTile)], mergeTiles1[(Int, MultibandTile)], mergeTiles2[(Int, MultibandTile)]) }
          .withContext { _.mapValues { _.sortBy(_._1).map(_._2).reduce(_ merge _) } }
          .stitch
      val craster =
        if(output.crop) input.mask.fold(raster)(mp => raster.crop(mp.envelope.reproject(LatLng, md.crs)))
        else raster

      def outputPath(root: String) = s"${root}/${input.resolution}-${UUID.randomUUID()}.tiff"
      val geoTiff = GeoTiff(craster, md.crs)

      output.source.getScheme match {
        case "dropbox" if exportDefinition.output.dropboxCredential.isDefined => {
          val client = dropboxConfig.client(exportDefinition.output.dropboxCredential.getOrElse(""))
          try {
            client
              .files
              .createFolder(output.source.getPath)
          } catch {
            case e: CreateFolderErrorException =>
              logger.warn(s"Target Path already exists, ${e.errorValue}")
          }
          geoTiff.dropboxWrite { is =>
            try {
              client
                .files
                .uploadBuilder(outputPath(output.source.getPath))
                .withMode(WriteMode.OVERWRITE)
                .uploadAndFinish(is)
                .getId
                .split("id:")
                .last
            } finally is.close()
          }
        }
        case _ => geoTiff.write(new Path(outputPath(output.source.toString)), wrappedConfiguration.get)
      }
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

    val exportDefinition =
      decode[ExportDefinition](readString(params.jobDefinition)) match {
        case Right(r) => r
        case _ => throw new Exception("Incorrect ExportDefinition JSON")
      }

    implicit val sc = new SparkContext(conf)
    try {
      exportProject(params)(exportDefinition)
    } finally {
      sc.stop
    }
  }
}
