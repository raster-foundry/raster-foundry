package com.azavea.rf.batch.export.spark

import java.io.ByteArrayInputStream

import com.azavea.rf.batch.export.json.S3ExportStatus
import com.azavea.rf.batch._
import com.azavea.rf.batch.ast._
import com.azavea.rf.batch.dropbox._
import com.azavea.rf.batch.export._
import com.azavea.rf.batch.util._
import com.azavea.rf.batch.util.conf._
import com.azavea.rf.common.utils.CogUtils
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast.MapAlgebraAST
import com.azavea.maml.eval._

import com.amazonaws.services.s3.AmazonS3URI
import com.amazonaws.services.s3.model.{ObjectMetadata, PutObjectRequest}
import cats.data.Validated._
import cats.effect.IO
import cats.implicits._
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.{CreateFolderErrorException, WriteMode}
import com.typesafe.scalalogging.LazyLogging
import doobie.Transactor
import io.circe.parser._
import io.circe.syntax._
import geotrellis.proj4.{CRS, LatLng}
import geotrellis.raster._
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.io._
import geotrellis.raster.io.geotiff.{GeoTiff, MultibandGeoTiff, SinglebandGeoTiff}
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import geotrellis.raster.resample.Bilinear
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.s3._
import geotrellis.spark.io.s3.util.S3RangeReader
import geotrellis.spark.regrid._
import geotrellis.spark.resample.ZoomResample
import geotrellis.spark.tiling._
import geotrellis.spark.util._
import geotrellis.vector._
import org.apache.hadoop.fs.Path
import spray.json.DefaultJsonProtocol._
import org.apache.spark.rdd.RDD
import org.apache.spark._

import java.util.UUID


object Export extends SparkJob with Config with LazyLogging {

  val jobName = "Export"

  val defaultRasterSize = 4000

  def s3Client = S3()

  def astExport(
    ed: ExportDefinition,
    ast: MapAlgebraAST,
    sceneLocs: Map[UUID, String],
    projLocs: Map[UUID, List[(UUID, String)]],
    conf: HadoopConfiguration
  )(implicit sc: SparkContext, xa: Transactor[IO]): IO[Unit] = {
    interpretRDD(ast, ed.input.resolution, sceneLocs, projLocs) map {
      case Invalid(errs) => throw InterpreterException(errs)
      case Valid(rdd) => {
        val crs: CRS = rdd.metadata.crs

        if(!ed.output.stitch) {
          val targetRDD: TileLayerRDD[SpatialKey] =
            ed.output.rasterSize match {
              case Some(size) => rdd.regrid(size)
              case None => rdd.regrid(defaultRasterSize)
            }

          val mt: MapKeyTransform = targetRDD.metadata.layout.mapTransform

          /* Create GeoTiffs and output them */
          val singles: RDD[(SpatialKey, SinglebandGeoTiff)] =
            targetRDD.map({ case (key, tile) => (key, SinglebandGeoTiff(tile, mt(key), crs)) })

          writeGeoTiffs[Tile, SinglebandGeoTiff](singles, ed, conf)
        } else {
          /* Stitch the Layer into a single GeoTiff and output it */
          val single: SinglebandGeoTiff = GeoTiff(rdd.stitch, crs)

          writeGeoTiff[Tile, SinglebandGeoTiff](single, ed, singlePath)
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

  def getAvroLayerRdd(ed: ExportDefinition, eld: ExportLayerDefinition, mask: Option[MultiPolygon])(implicit sc: SparkContext) = {
    val (reader, store) = getRfLayerManagement(eld)
    val requestedLayerId = LayerId(eld.layerId.toString, ed.input.resolution)

    val maxAvailableZoom =
      store
        .layerIds
        .filter { case LayerId(name, _) => name == requestedLayerId.name }
        .map { _.zoom }
        .max

    val maxLayerId = requestedLayerId.copy(zoom = maxAvailableZoom)
    val maxMetadata = store.readMetadata[TileLayerMetadata[SpatialKey]](maxLayerId)
    val maxMapTransform = maxMetadata.mapTransform

    val layoutScheme = ZoomedLayoutScheme(maxMetadata.crs, math.min(maxMetadata.tileCols, maxMetadata.tileRows))

    val requestedMapTransform = layoutScheme.levelForZoom(requestedLayerId.zoom).layout.mapTransform

    lazy val requestedQuery = reader.query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](requestedLayerId)
    lazy val maxQuery = reader.query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](maxLayerId)

    val hist =
      eld
        .colorCorrections
        .map { _ => store.read[Array[Histogram[Double]]](requestedLayerId.copy(zoom = 0), "histogram") }

    val queryLayer = (q: BoundLayerQuery[SpatialKey, TileLayerMetadata[SpatialKey], RDD[(SpatialKey, MultibandTile)] with Metadata[TileLayerMetadata[SpatialKey]]]) =>
    mask
      .fold(q)(mp => q.where(Intersects(mp)))
      .result
      .withContext(
        {
          rdd => rdd.mapValues(
            {
              tile =>
              val ctile = (eld.colorCorrections |@| hist) map { _.colorCorrect(tile, _) } getOrElse tile

              ed.output.render.flatMap(_.bands.map(_.toSeq)) match {
                case Some(seq) if seq.nonEmpty => ctile.subsetBands(seq)
                case _ => ctile
              }
            }
          )
        }
      )

    val query: MultibandTileLayerRDD[SpatialKey] =
      if (requestedLayerId.zoom <= maxAvailableZoom)
        queryLayer(requestedQuery)
      else
        ZoomResample(queryLayer(maxQuery), maxAvailableZoom, requestedLayerId.zoom)

    val md = query.metadata

    (ed.output.rasterSize, ed.output.crs) match {
      case (Some(rs), Some(crs)) =>
        query.reproject(ZoomedLayoutScheme(crs, rs))._2
      case (None, Some(crs)) =>
        query.reproject(ZoomedLayoutScheme(crs, defaultRasterSize))._2
      case (Some(rs), None) =>
        query.regrid(rs)
      case (None, None) =>
        query.regrid(defaultRasterSize)
    }
  }

  def getCOGLayerRdd(eld: ExportLayerDefinition)(implicit sc: SparkContext) =
    CogUtils.fromUriAsRdd(eld.ingestLocation.toString)

  def multibandExport(
    ed: ExportDefinition,
    layers: Array[ExportLayerDefinition],
    mask: Option[MultiPolygon],
    conf: HadoopConfiguration
  )(implicit @transient sc: SparkContext): Unit = {
    val rdds = layers.map { ld =>
      if (ld.sceneType == SceneType.Avro) getAvroLayerRdd(ed, ld, mask) else getCOGLayerRdd(ld)
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

      writeGeoTiff[MultibandTile, MultibandGeoTiff](GeoTiff(craster, md.crs), ed, singlePath)
    }
  }

  private def singlePath(ed: ExportDefinition): String =
    s"/${ed.output.getURLDecodedSource}/${ed.input.resolution}-${ed.id}-${UUID.randomUUID()}.tiff"

  def writeGeoTiffS3[T <: CellGrid, G <: GeoTiff[T]](tiff: G, path: String) = {
    val s3Uri = new AmazonS3URI(path)
    val bucket = s3Uri.getBucket
    val key = s3Uri.getKey
    val tiffBytes = tiff.toByteArray
    val inputStream = new ByteArrayInputStream(tiffBytes)
    val metadata = new ObjectMetadata()
    metadata.setContentLength(tiffBytes.length)
    metadata.setContentType("image/tiff")
    val putObjectRequest = new PutObjectRequest(bucket, key, inputStream, metadata)

    logger.info(s"Writing Geotiff to S3 s3://${bucket}/${key}")

    s3Client.putObject(putObjectRequest)
  }

  /** Write a single GeoTiff to some target. */
  private def writeGeoTiff[T <: CellGrid, G <: GeoTiff[T]](
    tiff: G,
    ed: ExportDefinition,
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
    case "s3" => {
      val s3uri = new AmazonS3URI(ed.output.source)
      val key = s3uri.getKey
      val bucket = s3uri.getBucket
      writeGeoTiffS3[T, G](tiff, path(ed))
    }
    case "file" => {
      logger.info(s"Writing File Output: ${path(ed)}")
      tiff.write(path(ed))
    }
    case _ => throw new Exception(s"Unknown schema for output location ${ed.output.source}")
  }

  /** Write a layer of GeoTiffs. */
  private def writeGeoTiffs[T <: CellGrid, G <: GeoTiff[T]](
    rdd: RDD[(SpatialKey, G)],
    ed: ExportDefinition,
    conf: HadoopConfiguration
  ): Unit = {

    def path(key: SpatialKey): ExportDefinition => String = { ed =>
      s"${ed.output.source.getPath}/${ed.input.resolution}-${key.col}-${key.row}-${ed.id}.tiff"
    }

    rdd.foreachPartition({ iter =>
      iter.foreach({ case (key, tile) => writeGeoTiff[T, G](tile, ed, path(key)) })
    })
  }

  /**
    *  Sample ingest definitions can be found in the accompanying test/resources
    *
    * @param args Arguments to be parsed by the tooling defined in [[CommandLine]]
    */
  def main(args: Array[String]): Unit = {
    implicit val xa = RFTransactor.xa

    val params = CommandLine.parser.parse(args, CommandLine.Params()) match {
      case Some(params) =>
        params
      case None =>
        throw new Exception("Unable to parse command line arguments")
    }

    val exportDef =
      decode[ExportDefinition](readString(params.jobDefinition)) match {
        case Right(r) => r
        case Left(e) => {
          logger.error(e.stackTraceString)
          throw new Exception("Incorrect ExportDefinition JSON")
        }
      }

    implicit val sc = new SparkContext(conf)


    implicit def asS3Payload(status: ExportStatus): String =
      S3ExportStatus(exportDef.id, status).asJson.noSpaces

    try {
      val conf: HadoopConfiguration = HadoopConfiguration(S3.setCredentials(sc.hadoopConfiguration))

      exportDef.input.style match {
        case Left(SimpleInput(layers, mask)) =>
          multibandExport(exportDef, layers, mask, conf)
        case Right(ASTInput(ast, sceneLocs, projLocs)) =>
          astExport(exportDef, ast, sceneLocs, projLocs, conf).unsafeRunSync
      }

      logger.info(s"Writing status into the ${params.statusURI}")
      s3Client.putObject(params.statusURI, ExportStatus.Exported)

    } catch {
      case t: Throwable =>
        logger.info(s"Writing status into the ${params.statusURI}")
        logger.error(t.stackTraceString)
        s3Client.putObject(params.statusURI, ExportStatus.Exported)
    } finally {
      sc.stop
    }
  }
}
