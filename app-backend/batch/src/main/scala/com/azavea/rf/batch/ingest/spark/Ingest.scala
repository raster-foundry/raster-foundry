package com.azavea.rf.batch.ingest.spark

import io.circe.parser._
import io.circe.syntax._

import com.azavea.rf.batch._
import com.azavea.rf.batch.ingest._
import com.azavea.rf.batch.ingest.json._
import com.azavea.rf.batch.ingest.model._
import com.azavea.rf.batch.util._
import com.azavea.rf.batch.util.conf.Config
import com.azavea.rf.common.S3.putObjectString
import com.azavea.rf.datamodel.IngestStatus

import geotrellis.raster._
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.io._
import geotrellis.raster.io.geotiff.MultibandGeoTiff
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.io.hadoop.HdfsRangeReader
import geotrellis.spark.io.http.util.HttpRangeReader
import geotrellis.spark.io.s3._
import geotrellis.spark.io.s3.util.S3RangeReader
import geotrellis.spark.util.KryoWrapper
import geotrellis.spark.pyramid.Pyramid
import geotrellis.spark.tiling._
import geotrellis.proj4.CRS
import geotrellis.util.{FileRangeReader, RangeReader}
import geotrellis.vector.ProjectedExtent

import com.amazonaws.services.s3.AmazonS3URI
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion
import com.typesafe.scalalogging.LazyLogging
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.spark._
import org.apache.spark.rdd._
import spray.json._
import spray.json.DefaultJsonProtocol._

import java.io.File
import java.net.URI
import java.util.UUID

object Ingest extends SparkJob with LazyLogging with Config {
  val jobName = "Ingest"

  type RfLayerWriter = Writer[LayerId, RDD[(SpatialKey, MultibandTile)] with Metadata[TileLayerMetadata[SpatialKey]]]
  type RfLayerDeleter = LayerDeleter[LayerId]

  /** Get a LayerWriter and an attribute store for the catalog located at the provided URI
    *
    * @param outputDef The ingest job's output definition
    */
  def getRfLayerManagement(outputDef: OutputDefinition): (RfLayerWriter, RfLayerDeleter, AttributeStore) = outputDef.uri.getScheme match {
    case "s3" | "s3a" | "s3n" =>
      val (bucket, prefix) = S3.parse(outputDef.uri)
      val s3Writer = S3LayerWriter(bucket, prefix)
      val writer = s3Writer.writer[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](outputDef.keyIndexMethod)
      val deleter = S3LayerDeleter(s3Writer.attributeStore)
      (writer, deleter, s3Writer.attributeStore)
    case "file" =>
      val fileWriter = FileLayerWriter(outputDef.uri.getPath)
      val writer = fileWriter.writer[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](outputDef.keyIndexMethod)
      val deleter = FileLayerDeleter(outputDef.uri.getPath)
      (writer, deleter, fileWriter.attributeStore)
  }

  def deleteLayer(deleter: RfLayerDeleter, layerId: LayerId): Unit = {
    try {
      deleter.delete(layerId)
    } catch { case _: Throwable =>
      // TODO: Bump GT version and use overwrite/clobber logic when the changes in
      //       https://github.com/locationtech/geotrellis/pull/2039 are released.
      //       This fix should be available in GT ver. 1.0.1.
      //       This is *NOT* a permanent fix and will NOT resolve similar issues encountered
      //       locally (this should be fine as S3 ingests are the focus in the near term).
      deleter match { case del: S3LayerDeleter => // This is always the case (we just need type refinement)
        del.attributeStore match {
          case attStore: S3AttributeStore =>
            logger.info(s"Overwritten layer metadata $layerId not found. Proceeding with attribute deletion...")
            val s3Client = S3Client.DEFAULT
            val listing = s3Client
              .listObjectsIterator(attStore.bucket, attStore.path(attStore.prefix, "_attributes"))
              .map { _.getKey }
              .filter { _.contains(s"${S3AttributeStore.SEP}${layerId.name}${S3AttributeStore.SEP}${layerId.zoom}.json") }
              .map { key => new KeyVersion(key) }
              .toList

              s3Client.deleteObjects(attStore.bucket, listing)
          case _ =>
            logger.error("This branch should be unreachable from GT LayerDeleter instances")
        }
      }
    }
  }

  /** Produce metadata for an IngestLayer
    *
    *  @param layer A specification for ingesting a layer
    *  @param scheme A scheme used to construct the tiling grid
    */
  def calculateTileLayerMetadata(layer: IngestLayer, scheme: LayoutScheme): (Int, TileLayerMetadata[SpatialKey]) = {
    // We need to build TileLayerMetadata that we expect to start pyramid from
    val overallExtent = layer.sources
      .map({ src => src.extent.reproject(src.extentCrs, layer.output.crs) })
      .reduce(_ combine _)

    // Infer the base level of the TMS pyramid based on overall extent and cellSize
    // We should use the LayoutLevel with the greatest resolution - hence maxBy here
    val LayoutLevel(maxZoom, baseLayoutDefinition) =
      layer.sources.map({ source =>
        scheme.levelFor(overallExtent, source.cellSize)
      }).maxBy(_.zoom)

    val meta = TileLayerMetadata(
      cellType = layer.output.cellType,
      layout = baseLayoutDefinition,
      extent = overallExtent,
      crs = layer.output.crs,
      bounds = {
        val GridBounds(colMin, rowMin, colMax, rowMax) =
          baseLayoutDefinition.mapTransform(overallExtent)
        KeyBounds(
          SpatialKey(colMin, rowMin),
          SpatialKey(colMax, rowMax)
        )
      }
    )

    (maxZoom, meta)
  }

  /** Produce a multiband histogram
    *
    * @param rdd An RDD of Tiles to construct a histogram over
    * @param numBuckets The number of histogram 'buckets' in which to bin values
    */
  def multibandHistogram(rdd: RDD[(SpatialKey, MultibandTile)], numBuckets: Int): Vector[Histogram[Double]] =
    rdd.map { case (_, mbt) => mbt.bands.map(_.histogramDouble(numBuckets)) }
      .reduce((hs1, hs2) => hs1.zip(hs2).map { case (a, b) => a merge b })
  // .reduce((hs1, hs2) => (hs1, hs2).parMap2(_ merge _))  // Once Cats 1.0 is released.


  /** Read a single GeoTiff into an RDD
    * The RDD will have number of partitions dictated by the maximum partition byte size.
    * The RDD records will be windows over the segment layout, buffered by some pixels.
    * If pixelBuffer is greater than 0 the windows will overlap by that many pixels.
    * Each partition will only read those segments which intersect the windows it  contains.
    */
  def readGeoTiffToRDD(
    uri: String,
    maxTileSize:Int,
    pixelBuffer: Int,
    partitionBytes: Long
  )(implicit sc: SparkContext): RDD[(ProjectedExtent, MultibandTile)] = {
    def readInfo = {
      val s3uri = new AmazonS3URI(uri)
      GeoTiffReader.readGeoTiffInfo(
      S3RangeReader(
        bucket = s3uri.getBucket,
        key = s3uri.getKey,
        client = S3Client.DEFAULT),
      decompress = false, streaming = true)
    }

    val info = readInfo

    // This listing can be masked by Geometry if desired
    val windows: Array[GridBounds] = info
      .segmentLayout
      .listWindows(maxTileSize)
      .map(_.buffer(pixelBuffer))

    val partitions: Array[Array[GridBounds]] = info.segmentLayout.partitionWindowsBySegments(
      windows, partitionBytes / math.max(info.cellType.bytes, 1))

    val kryoInfo = KryoWrapper(info)

    sc.parallelize(partitions, partitions.length).flatMap { bounds =>
      // re-constructing here to avoid serialization pit-falls
      val info = readInfo
      val geoTiff = GeoTiffReader.geoTiffMultibandTile(info)
      val windows = geoTiff.crop(bounds.filter(geoTiff.gridBounds.intersects))

      windows.map { case (bound, tile) =>
        val extent = info.rasterExtent.extentFor(bound, clamp = false)
        ProjectedExtent(extent, info.crs) -> tile
      }
    }
  }

  /** We need to suppress this warning because there's a perfectly safe `head` call being
    *  made here. The compiler just isn't smart enough to figure that out
    *
    *  @param layer An ingest layer specification
    */
  @SuppressWarnings(Array("TraversableHead"))
  def ingestLayer(params: CommandLine.Params, layer: IngestLayer)(implicit sc: SparkContext): Unit = {

    val resampleMethod = layer.output.resampleMethod
    val tileSize = layer.output.tileSize
    val destCRS = layer.output.crs
    val ndPattern = layer.output.ndPattern
    val bandCount: Int = layer.sources.map(_.bandMaps.map(_.target.index).max).max
    val layoutScheme = ZoomedLayoutScheme(destCRS, tileSize)

    val (maxZoom, layerMeta): (Int, TileLayerMetadata[SpatialKey]) =
      calculateTileLayerMetadata(layer, layoutScheme)

    val rawMultis: Array[RDD[((ProjectedExtent, Int), Tile)]] = layer.sources.map { source =>
      /* The target band number is reduced by one, since those start at 1 to match Landsat. */
      val bandMap: Map[Int, Int] = source.bandMaps.map(bm => (bm.source - 1, bm.target.index - 1)).toMap

      readGeoTiffToRDD(
        uri = java.net.URLDecoder.decode(source.uri.toString, "UTF-8"),
        maxTileSize = tileSize,
        pixelBuffer = 4,
        partitionBytes = 16 * 1024 * 1024
      ).flatMap { case (ProjectedExtent(extent, srcCRS), mbt) =>
          // Set NoData values if a pattern has been specified
          val maskedChip = ndPattern.fold(mbt)(mask => mask(mbt))

          bandMap.flatMap { case (sourceIndex, targetIndex) =>
            maskedChip.bandSafe(sourceIndex).map { tile =>
              val reprojected = Raster(tile, extent).reproject(srcCRS, destCRS, resampleMethod)
              val reprojectedExtent = ProjectedExtent(reprojected.extent, destCRS)

              (reprojectedExtent, targetIndex) -> reprojected.tile
            }
          }
      }
    }

    val tiledChips: RDD[((SpatialKey, Int), Tile)] =
      sc.union(rawMultis).tileToLayout(layerMeta.cellType, layerMeta.layout, resampleMethod)

    /* After reading imagery from S3, we need to shuffle bands to create desired band mapping. */
    val assembledTiles: RDD[(SpatialKey, MultibandTile)] =
      tiledChips
        .map { case ((key, band), tile) => key -> (band, tile) }
        .groupByKey
        .map { case (key, tiles) =>
        val prototype: Tile = tiles.head._2
        val emptyTile: Tile = ArrayTile.empty(prototype.cellType, prototype.cols, prototype.rows)
        val sourceBands = tiles.toMap
        val bands: Seq[Tile] =
          for (bandIndex <- 0 until bandCount)
          yield sourceBands.getOrElse(bandIndex, emptyTile)
        key -> MultibandTile(bands)
      }


    val layerRdd: RDD[(SpatialKey, MultibandTile)] with Metadata[TileLayerMetadata[SpatialKey]] =
      ContextRDD(assembledTiles, layerMeta)

    val (writer, deleter, attributeStore) = getRfLayerManagement(layer.output)

    val sharedId: LayerId = LayerId(layer.id.toString, 0)

    /* If a layer of the same already exists and we've decided to overwrite it,
     * then we go ahead and do so.
     */
    if (params.overwrite && attributeStore.layerExists(sharedId)) { deleteLayer(deleter, sharedId) }

    logger.info("Writing layers")
    attributeStore.write(sharedId, "ingestComplete", false)
    if (layer.output.pyramid) { // If pyramiding
      Pyramid.upLevels(layerRdd, layoutScheme, maxZoom, 1, resampleMethod) { (rdd, zoom) =>
        logger.info(s"Writing zoom level $zoom in ${layer.id.toString}")
        val layerId = LayerId(layer.id.toString, zoom)
        if (params.overwrite && attributeStore.layerExists(layerId)) { deleteLayer(deleter, layerId) }
        attributeStore.write(layerId, "layerComplete", false)
        writer.write(layerId, rdd)

        if (zoom == math.max(maxZoom / 2, 1)) {
          attributeStore.write(sharedId, "histogram", multibandHistogram(rdd, numBuckets = 256))
        }

        if (zoom == 1) {
          attributeStore.write(sharedId, "extent", rdd.metadata.extent)(ExtentJsonFormat) // avoid using default JF
          attributeStore.write(sharedId, "crs", rdd.metadata.crs)(CRSJsonFormat) // avoid using default JF
        }
        attributeStore.write(layerId, "layerComplete", true)
      }
    } else { // If not pyramiding. TODO: figure out exactly what we want to store here
      logger.info(s"Writing (no pyramid) layer ${layer.id.toString}")
      writer.write(sharedId, layerRdd)
    }
    attributeStore.write(sharedId, "ingestComplete", true)
    logger.info("Ingest complete")
  }

  /** Sample ingest definitions can be found in the accompanying test/resources
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

    val ingestDefinition = decode[IngestDefinition](readString(params.jobDefinition)) match {
      case Right(r) => r
      case _ => throw new Exception("Incorrect IngestDefinition JSON")
    }

    val sceneId = UUID.fromString(params.sceneId)

    /* Warn about ignored flags */
    if (params.windowSize.isDefined) logger.warn("windowSize parameter was explicitely set, but will be ignored.")
    if (params.partitionsPerFile.isDefined) logger.warn("partitionsPerFile parameter was explicitely set, but will be ignored.")
    if (params.partitionsSize.isDefined) logger.warn("partitionsSize parameter was explicitely set, but will be ignored.")

    implicit val sc = new SparkContext(conf)

    implicit def asS3Payload(status: IngestStatus): String = S3IngestStatus(sceneId, status).asJson.noSpaces

    try {
      ingestDefinition.layers.foreach(ingestLayer(params, _))
      if (params.testRun) ingestDefinition.layers.foreach(Validation.validateCatalogEntry)
      putObjectString(
        params.statusBucket,
        ingestDefinition.id.toString,
        IngestStatus.Ingested
      )
    } catch {
      case t: Throwable =>
        logger.error(t.stackTraceString)
        putObjectString(
          params.statusBucket,
          ingestDefinition.id.toString,
          IngestStatus.Failed
        )
    } finally {
      sc.stop
    }
  }
}
