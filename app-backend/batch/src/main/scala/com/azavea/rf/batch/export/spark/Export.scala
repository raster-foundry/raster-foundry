package com.azavea.rf.batch.export.spark

import com.azavea.rf.batch.export.json.S3ExportStatus
import com.azavea.rf.batch._
import com.azavea.rf.batch.ast._
import com.azavea.rf.batch.dropbox._
import com.azavea.rf.batch.export._
import com.azavea.rf.batch.util._
import com.azavea.rf.batch.util.conf._
import com.azavea.rf.common.ast.InterpreterException
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast.MapAlgebraAST
import com.azavea.rf.tool.params.EvalParams
import com.azavea.rf.common.S3.putObject

import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.{CreateFolderErrorException, WriteMode}
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser._
import io.circe.syntax._
import cats.data.Validated._
import cats.implicits._
import com.dropbox.core.v2.DbxClientV2
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
import geotrellis.spark.tiling._
import geotrellis.vector.MultiPolygon
import org.apache.hadoop.fs.Path
import spray.json.DefaultJsonProtocol._
import org.apache.spark.rdd.RDD
import org.apache.spark._

import java.util.UUID


object Export extends SparkJob with Config with LazyLogging {

  val jobName = "Export"

  def astExport(
    ed: ExportDefinition,
    ast: MapAlgebraAST,
    params: EvalParams,
    sceneLocs: Map[UUID, String],
    projLocs: Map[UUID, List[(UUID, String)]],
    conf: HadoopConfiguration
  )(implicit sc: SparkContext): Unit = {
    interpretRDD(ast, params.sources, params.overrides, ed.input.resolution, sceneLocs, projLocs) match {
      case Invalid(errs) => throw InterpreterException(errs)
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

          writeGeoTiff[Tile, SinglebandGeoTiff](single, ed, conf, singlePath)
        }
      }
    }
  }

  /** Get a LayerReader and an attribute store for the catalog located at the provided URI
    *
    *  @param ed export job's layer definition
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

      writeGeoTiff[MultibandTile, MultibandGeoTiff](GeoTiff(craster, md.crs), ed, conf, singlePath)
    }
  }

  private def singlePath(ed: ExportDefinition): String =
    s"/${ed.output.getURLDecodedSource}/${ed.input.resolution}-${ed.id}-${UUID.randomUUID()}.tiff"

  /** Write a single GeoTiff to some target. */
  private def writeGeoTiff[T <: CellGrid, G <: GeoTiff[T]](
    tiff: G,
    ed: ExportDefinition,
    conf: HadoopConfiguration,
    path: ExportDefinition => String
  ): Unit = ed.output.source.getScheme match {
    case "dropbox" if ed.output.dropboxCredential.isDefined => {
      val client: DbxClientV2 = dropboxConfig.client(ed.output.dropboxCredential.getOrElse(""))

      try {
        client.files.createFolder(ed.output.source.getPath)
      } catch {
        case e: CreateFolderErrorException =>
          logger.warn(s"Target Path already exists, ${e.errorValue}")
      }
      tiff.dropboxWrite { is =>
        try {
          client
            .files
            .uploadBuilder(path(ed))
            .withMode(WriteMode.OVERWRITE)
            .uploadAndFinish(is)
            .getId
            .split("id:")
            .last
        } finally is.close()
      }
    }
    case _ => tiff.write(new Path(path(ed)), conf.get)
  }

  /** Write a layer of GeoTiffs. */
  private def writeGeoTiffs[T <: CellGrid, G <: GeoTiff[T]](
    rdd: RDD[(SpatialKey, G)],
    ed: ExportDefinition,
    conf: HadoopConfiguration
  ): Unit = {
    def path(key: SpatialKey): ExportDefinition => String = { ed =>
      s"/${ed.output.getURLDecodedSource}/${ed.input.resolution}-${key.col}-${key.row}-${ed.id}.tiff"
    }

    rdd.foreachPartition({ iter =>
      iter.foreach({ case (key, tile) => writeGeoTiff[T, G](tile, ed, conf, path(key)) })
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

    implicit def asS3Payload(status: ExportStatus): String =
      S3ExportStatus(exportDef.id, status).asJson.noSpaces

    try {
      val conf: HadoopConfiguration =
        HadoopConfiguration(S3.setCredentials(sc.hadoopConfiguration))

      exportDef.input.style match {
        case Left(SimpleInput(layers, mask)) =>
          multibandExport(exportDef, layers, mask, conf)
        case Right(ASTInput(ast, params, sceneLocs, projLocs)) =>
          astExport(exportDef, ast, params, sceneLocs, projLocs, conf)
      }

      logger.info(s"Writing status into the ${params.statusBucket}/${exportDef.id}")
      putObject(
        params.statusBucket,
        exportDef.id.toString,
        ExportStatus.Exported
      )
    } catch {
      case t: Throwable =>
        logger.info(s"Writing status into the ${params.statusBucket}/${exportDef.id}")
        logger.error(t.stackTraceString)
        putObject(
          params.statusBucket,
          exportDef.id.toString,
          ExportStatus.Failed
        )
    } finally {
      sc.stop
    }
  }
}
