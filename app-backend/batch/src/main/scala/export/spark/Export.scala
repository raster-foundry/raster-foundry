package com.rasterfoundry.batch.export.spark

import java.io.ByteArrayInputStream
import java.net.URI
import java.util.UUID

import cats.data.Validated._
import cats.effect.IO
import cats.implicits._
import com.amazonaws.services.s3.AmazonS3URI
import com.amazonaws.services.s3.model.{ObjectMetadata, PutObjectRequest}
import com.azavea.maml.eval._
import com.rasterfoundry.batch._
import com.rasterfoundry.batch.ast._
import com.rasterfoundry.batch.dropbox._
import com.rasterfoundry.batch.export._
import com.rasterfoundry.batch.export.json.S3ExportStatus
import com.rasterfoundry.batch.util._
import com.rasterfoundry.batch.util.conf._
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.common.utils.CogUtils
import com.rasterfoundry.database._
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.datamodel._
import com.rasterfoundry.tool.ast.MapAlgebraAST
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.{CreateFolderErrorException, WriteMode}
import com.typesafe.scalalogging.LazyLogging
import doobie.Transactor
import doobie.implicits._
import geotrellis.proj4.{CRS, LatLng}
import io.circe.parser._
import io.circe.syntax._
import geotrellis.raster._
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.io._
import geotrellis.raster.io.geotiff.{
  GeoTiff,
  MultibandGeoTiff,
  SinglebandGeoTiff
}
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.io.s3._
import geotrellis.spark.resample.ZoomResample
import geotrellis.spark.tiling._
import geotrellis.vector._
import org.apache.spark._
import org.apache.spark.rdd.RDD
import spray.json.DefaultJsonProtocol._

object Export extends SparkJob with Config with RollbarNotifier {

  val jobName = "Export"

  val defaultRasterSize = 4000

  def s3Client = S3()

  def astExport(
      ed: ExportDefinition,
      ast: MapAlgebraAST,
      projLocs: Map[UUID, List[(UUID, String)]]
  )(implicit sc: SparkContext, xa: Transactor[IO]): IO[Unit] = {
    interpretRDD(ast, ed.input.resolution, projLocs) map {
      case Invalid(errs) => throw InterpreterException(errs)
      case Valid(rdd) => {
        val crs: CRS = rdd.metadata.crs

        val targetRDD: TileLayerRDD[SpatialKey] =
          ed.output.rasterSize match {
            case Some(size) => rdd.regrid(size)
            case None       => rdd.regrid(defaultRasterSize)
          }

        val mt: MapKeyTransform = targetRDD.metadata.layout.mapTransform

        /* Create GeoTiffs and output them */
        val singles: RDD[(SpatialKey, SinglebandGeoTiff)] =
          targetRDD.map({
            case (key, tile) => (key, SinglebandGeoTiff(tile, mt(key), crs))
          })

        writeGeoTiffs[Tile, SinglebandGeoTiff](singles, ed)
      }
    }
  }

  /** Get a LayerReader and an attribute store for the catalog located at the provided URI
    */
  def getRfLayerManagement(
      mosaicDefinition: MosaicDefinition
  )(implicit @transient sc: SparkContext)
    : (FilteringLayerReader[LayerId], AttributeStore) = {
    (mosaicDefinition.ingestLocation map { new URI(_) },
     mosaicDefinition.ingestLocation map {
       new URI(_).getScheme
     }) match {
      case (Some(loc), Some("s3")) =>
        val (bucket, prefix) = S3.parse(loc)
        val reader = S3LayerReader(bucket, prefix)
        (reader, reader.attributeStore)
      case (Some(loc), Some("s3a")) =>
        val (bucket, prefix) = S3.parse(loc)
        val reader = S3LayerReader(bucket, prefix)
        (reader, reader.attributeStore)
      case (Some(loc), Some("s3n")) =>
        val (bucket, prefix) = S3.parse(loc)
        val reader = S3LayerReader(bucket, prefix)
        (reader, reader.attributeStore)
      case (Some(loc), Some("file")) =>
        val reader = FileLayerReader(loc.getPath)
        (reader, reader.attributeStore)
      case _ =>
        throw new Exception("Scene had no ingest location or unknown scheme")
    }
  }

  def getAvroLayerRdd(ed: ExportDefinition,
                      mosaicDefinition: MosaicDefinition,
                      mask: Option[MultiPolygon],
                      raw: Boolean)(implicit sc: SparkContext) = {
    val (reader, store) = getRfLayerManagement(mosaicDefinition)
    val requestedLayerId =
      LayerId(mosaicDefinition.sceneId.toString, ed.input.resolution)

    val maxAvailableZoom =
      store.layerIds
        .filter { case LayerId(name, _) => name == requestedLayerId.name }
        .map { _.zoom }
        .max

    val maxLayerId = requestedLayerId.copy(zoom = maxAvailableZoom)
    val maxMetadata =
      store.readMetadata[TileLayerMetadata[SpatialKey]](maxLayerId)
    val maxMapTransform = maxMetadata.mapTransform

    val layoutScheme = ZoomedLayoutScheme(
      maxMetadata.crs,
      math.min(maxMetadata.tileCols, maxMetadata.tileRows))

    val requestedMapTransform =
      layoutScheme.levelForZoom(requestedLayerId.zoom).layout.mapTransform

    lazy val requestedQuery =
      reader.query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](
        requestedLayerId)
    lazy val maxQuery =
      reader.query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](
        maxLayerId)

    val hist =
      if (raw) None
      else
        Some(
          store.read[Array[Histogram[Double]]](requestedLayerId.copy(zoom = 0),
                                               "histogram"))

    val queryLayer =
      (q: BoundLayerQuery[SpatialKey,
                          TileLayerMetadata[SpatialKey],
                          RDD[(SpatialKey, MultibandTile)] with Metadata[
                            TileLayerMetadata[SpatialKey]]]) =>
        mask
          .fold(q)(mp => q.where(Intersects(mp)))
          .result
          .withContext(
            { rdd =>
              rdd.mapValues(
                { tile =>
                  val ctile = (Some(mosaicDefinition.colorCorrections), hist) mapN {
                    _.colorCorrect(tile, _, None)
                  } getOrElse tile

                  ed.output.render.flatMap(_.bands.map(_.toSeq)) match {
                    case Some(seq) if seq.nonEmpty => ctile.subsetBands(seq)
                    case _                         => ctile
                  }
                }
              )
            }
        )

    val query: MultibandTileLayerRDD[SpatialKey] =
      if (requestedLayerId.zoom <= maxAvailableZoom)
        queryLayer(requestedQuery)
      else
        ZoomResample(queryLayer(maxQuery),
                     maxAvailableZoom,
                     requestedLayerId.zoom)

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

  def getCOGLayerRdd(mosaicDefinition: MosaicDefinition)(
      implicit sc: SparkContext) =
    CogUtils.fromUriAsRdd(mosaicDefinition.ingestLocation getOrElse {
      s"Cog ${mosaicDefinition.sceneId} has no ingest location"
    })

  def multibandExport(
      ed: ExportDefinition,
      layers: Array[MosaicDefinition],
      mask: Option[MultiPolygon],
      raw: Boolean
  )(implicit @transient sc: SparkContext): Unit = {
    val rdds = layers.map { ld =>
      ld.sceneType map {
        case SceneType.Avro =>
          getAvroLayerRdd(ed, ld, mask, raw)
        case SceneType.COG =>
          getCOGLayerRdd(ld)
      } getOrElse { getAvroLayerRdd(ed, ld, mask, raw) }
    }

    /** Tile merge with respect to layer initial ordering */
    val result: RDD[(SpatialKey, MultibandGeoTiff)] =
      rdds.zipWithIndex
        .map {
          case (rdd, i) =>
            val md = rdd.metadata
            rdd.map {
              case (key, tile) =>
                (key, i -> GeoTiff(tile, md.mapTransform(key), md.crs))
            }
        }
        .reduce(_ union _)
        .combineByKey(createTiles[(Int, MultibandGeoTiff)],
                      mergeTiles1[(Int, MultibandGeoTiff)],
                      mergeTiles2[(Int, MultibandGeoTiff)])
        .mapValues { seq =>
          val sorted = seq.sortBy(_._1).map(_._2)
          sorted.headOption map { head =>
            GeoTiff(sorted.map(_.tile).reduce(_ merge _), head.extent, head.crs)
          }
        }
        .flatMapValues(v => v)

    writeGeoTiffs[MultibandTile, MultibandGeoTiff](result, ed)
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
    val putObjectRequest =
      new PutObjectRequest(bucket, key, inputStream, metadata)

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
      val client: DbxClientV2 =
        dropboxConfig.client(ed.output.dropboxCredential.getOrElse(""))

      try {
        client.files.createFolderV2(ed.output.source.getPath, false)
      } catch {
        case e: CreateFolderErrorException =>
          logger.warn(s"Target Path already exists, ${e.errorValue}")
      }
      tiff.dropboxWrite { is =>
        try {
          client.files
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
    case _ =>
      throw new Exception(
        s"Unknown schema for output location ${ed.output.source}")
  }

  /** Write a layer of GeoTiffs. */
  private def writeGeoTiffs[T <: CellGrid, G <: GeoTiff[T]](
      rdd: RDD[(SpatialKey, G)],
      ed: ExportDefinition
  ): Unit = {

    def path(key: SpatialKey): ExportDefinition => String = { ed =>
      s"${ed.output.source.getPath}/${ed.input.resolution}-${key.col}-${key.row}-${ed.id}.tiff"
    }

    rdd.foreachPartition({ iter =>
      iter.foreach({
        case (key, tile) => writeGeoTiff[T, G](tile, ed, path(key))
      })
    })
  }

  /**
    * Sample ingest definitions can be found in the accompanying test/resources
    *
    * @param args Arguments to be parsed by the tooling defined in [[CommandLine]]
    */
  @SuppressWarnings(Array("CatchThrowable")) // need to ensure that status is written for errors
  def main(args: Array[String]): Unit = {
    val xaResource = RFTransactor.xaResource

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

    // Note: these logs don't actually work independent of log level and I have no idea why
    // You can set them to info and check 'Output from export command' in the logs from
    // running `rf export`, and you'll get nothing. It's pretty annoying!
    val runIO: IO[Unit] = xaResource.use(xa => {
      implicit val transactor = xa
      for {
        _ <- logger.debug("Fetching system user").pure[IO]
        user <- UserDao.unsafeGetUserById(systemUser).transact(xa)
        _ <- logger.debug(s"Fetching export ${exportDef.id}").pure[IO]
        export <- ExportDao.unsafeGetExportById(exportDef.id).transact(xa)
        exportOptions = export.exportOptions.as[ExportOptions]
        _ <- logger.debug(s"Performing export").pure[IO]
        result <- exportDef.input.style match {
          case SimpleInput(layers, mask) =>
            IO(multibandExport(exportDef, layers, mask, exportOptions map {
              _.raw
            } getOrElse false)).attempt
          case ASTInput(ast, _, projLocs) =>
            IO(astExport(exportDef, ast, projLocs).unsafeRunSync).attempt
        }
      } yield {
        result match {
          case Right(_) => ()
          case Left(throwable) =>
            sendError(throwable)
            throw throwable
        }
      }
    }) handleErrorWith {
      case e: Throwable =>
        IO {
          sc.stop()
          sendError(e)
          logger.error(e.stackTraceString)
          System.exit(1)
        }
    }
    runIO.unsafeRunSync
    sc.stop()
    System.exit(0)
  }
}
