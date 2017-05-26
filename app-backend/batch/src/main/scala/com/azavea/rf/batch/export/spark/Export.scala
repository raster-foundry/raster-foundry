package com.azavea.rf.batch.export.spark

import io.circe.parser._
import com.azavea.rf.datamodel._
import com.azavea.rf.batch._
import com.azavea.rf.batch.util._
import com.azavea.rf.batch.export._
import com.azavea.rf.batch.dropbox._
import com.azavea.rf.batch.util.conf._

import geotrellis.proj4.{CRS, LatLng}
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
import org.apache.spark.rdd.RDD
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

  def exportProject(
    params: CommandLine.Params,
    exportDef: ExportDefinition
  )(implicit @transient sc: SparkContext): Unit = {

    val wrappedConfiguration = HadoopConfiguration(S3.setCredentials(sc.hadoopConfiguration))

    val rdds: Array[RDD[(SpatialKey, MultibandTile)] with Metadata[TileLayerMetadata[SpatialKey]]] =
      exportDef.input.layers.map { ld =>
        val (reader, store) = getRfLayerManagement(ld)

        val layerId = LayerId(ld.layerId.toString, exportDef.input.resolution)

        val md: TileLayerMetadata[SpatialKey] =
          store.readMetadata[TileLayerMetadata[SpatialKey]](layerId)

        val hist: Option[Array[Histogram[Double]]] =
          ld.colorCorrections.map(_ =>
            store.read[Array[Histogram[Double]]](LayerId(layerId.name, 0), "histogram")
          )

        val crs: CRS = exportDef.output.crs.getOrElse(md.crs)

        val q = reader.query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](layerId)

        /* A Layer to operate on, and then export */
        val query: RDD[(SpatialKey, MultibandTile)] with Metadata[TileLayerMetadata[SpatialKey]] =
          exportDef.input.mask
            .fold(q)(mp => q.where(Intersects(mp.reproject(LatLng, md.crs))))
            .result
            .withContext({ rdd => rdd.mapValues({ tile =>
              val correctedTile: MultibandTile =
                (ld.colorCorrections |@| hist).map(_.colorCorrect(tile, _)).getOrElse(tile)

              exportDef.output.render.flatMap(_.bands.map(_.toSeq)) match {
                case Some(seq) if seq.nonEmpty => correctedTile.subsetBands(seq)
                case _ => correctedTile
              }
            })})

      exportDef.output.rasterSize match {
        case Some(rs) if md.crs != crs || rs != md.tileCols || rs != md.tileRows =>
          query.reproject(ZoomedLayoutScheme(crs, rs))._2
        case _ if md.crs != crs =>
          query.reproject(ZoomedLayoutScheme(crs, math.min(md.tileCols, md.tileRows)))._2
        case _ => query
      }
    }

    /** Tile merge with respect to layer initial ordering */
    if(!exportDef.output.stitch) {
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

      result.foreachPartition { iter =>
        val configuration = wrappedConfiguration.get
        iter.foreach { case (key, tile) =>
          tile.write(
            new Path(s"${exportDef.output.source.toString}/${exportDef.input.resolution}-${key.col}-${key.row}.tiff"),
            configuration
          )
        }
      }
    } else {
      val md: TileLayerMetadata[SpatialKey] = rdds.map(_.metadata).reduce(_ combine _)

      val raster: Raster[MultibandTile] =
        rdds
          .zipWithIndex
          .map { case (rdd, i) => rdd.mapValues { value => i -> value } }
          .foldLeft(ContextRDD(sc.emptyRDD[(SpatialKey, (Int, MultibandTile))], md))((acc, r) => acc.withContext { _.union(r) })
          .withContext { _.combineByKey(createTiles[(Int, MultibandTile)], mergeTiles1[(Int, MultibandTile)], mergeTiles2[(Int, MultibandTile)]) }
          .withContext { _.mapValues { _.sortBy(_._1).map(_._2).reduce(_ merge _) } }
          .stitch

      val craster: Raster[MultibandTile] =
        if(exportDef.output.crop) exportDef.input.mask.fold(raster)(mp => raster.crop(mp.envelope.reproject(LatLng, md.crs)))
        else raster

      def outputPath(root: String) = s"${root}/${exportDef.input.resolution}-${UUID.randomUUID()}.tiff"
      val geoTiff = GeoTiff(craster, md.crs)

      exportDef.output.source.getScheme match {
        case "dropbox" if exportDef.output.dropboxCredential.isDefined => {
          val client = dropboxConfig.client(exportDef.output.dropboxCredential.getOrElse(""))
          try {
            client
              .files
              .createFolder(exportDef.output.source.getPath)
          } catch {
            case e: CreateFolderErrorException =>
              logger.warn(s"Target Path already exists, ${e.errorValue}")
          }
          geoTiff.dropboxWrite { is =>
            try {
              client
                .files
                .uploadBuilder(outputPath(exportDef.output.source.getPath))
                .withMode(WriteMode.OVERWRITE)
                .uploadAndFinish(is)
                .getId
                .split("id:")
                .last
            } finally is.close()
          }
        }
        case _ => geoTiff.write(new Path(outputPath(exportDef.output.source.toString)), wrappedConfiguration.get)
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

    val exportDef =
      decode[ExportDefinition](readString(params.jobDefinition)) match {
        case Right(r) => r
        case _ => throw new Exception("Incorrect ExportDefinition JSON")
      }

    implicit val sc = new SparkContext(conf)

    try {
      exportProject(params, exportDef)
    } finally {
      sc.stop
    }
  }
}
