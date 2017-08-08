package com.azavea.rf.batch.ingest.spark

import io.circe.parser._
import io.circe.syntax._

import com.azavea.rf.batch._
import com.azavea.rf.batch.ingest._
import com.azavea.rf.batch.ingest.json._
import com.azavea.rf.batch.ingest.model._
import com.azavea.rf.batch.util._
import com.azavea.rf.batch.util.conf.Config
import com.azavea.rf.common.S3.putObject
import com.azavea.rf.datamodel.IngestStatus

import geotrellis.raster._
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.io._
import geotrellis.raster.io.geotiff.MultibandGeoTiff
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.io.hadoop.HdfsRangeReader
import geotrellis.spark.io.http.util.HttpRangeReader
import geotrellis.spark.io.s3._
import geotrellis.spark.io.s3.util.S3RangeReader
import geotrellis.spark.pyramid.Pyramid
import geotrellis.spark.tiling._
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

  def deleteLayer(deleter: RfLayerDeleter)(layerId: LayerId): Unit = {
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

    maxZoom -> TileLayerMetadata(
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
  }

  /** Produce a multiband histogram
    *
    * @param rdd An RDD of Tiles to construct a histogram over
    * @param numBuckets The number of histogram 'buckets' in which to bin values
    */
  def multibandHistogram(rdd: RDD[(SpatialKey, MultibandTile)], numBuckets: Int): Vector[Histogram[Double]] =
    rdd.map({ case (key, mbt) =>
      mbt.bands.map { tile =>
        tile.histogramDouble(numBuckets)
      }
    }).reduce({ (hs1, hs2) =>
      hs1.zip(hs2).map { case (a, b) => a merge b }
    })


  def uriRangReader(uri: URI): RangeReader = {
    uri.getScheme match {
      case "hdfs" =>
        HdfsRangeReader(new Path(uri), new Configuration)
      case "file" =>
        FileRangeReader(new File(uri))
      case "s3" | "s3a" | "s3n" =>
        val (bucket, prefix) = S3.parse(uri)
        val decodedPrefix = java.net.URLDecoder.decode(prefix, "UTF-8")
        S3RangeReader(bucket, decodedPrefix, S3Client.DEFAULT)
      case "http" | "https"
          if uri.getAuthority == "s3.amazonaws.com"
          && uri.getQuery.contains("AWSAccessKeyId") =>
        // Signed S3 URLs don't support HEAD requests
        new HttpRangeReader(uri.toURL(), useHeadRequest = false)
      case "http" | "https" =>
        new HttpRangeReader(uri.toURL(), useHeadRequest = true)
    }
  }

  /** Function to add GridBounds buffer */
  def bufferGrid(gb: GridBounds, by: Int = 4) =
    gb.copy(
      colMin = if(gb.colMin < by) 0 else gb.colMin - by,
      colMax = gb.colMax + by,
      rowMin = if(gb.rowMax < by) 0 else gb.rowMin - by,
      rowMax = gb.rowMax + by
    )

  /** Chip out a grid bounds into component pieces of at least given size */
  def gridBoundChips(gb: GridBounds, chipWidth: Int, chipHeight: Int): Iterator[GridBounds] = {
    val cw = math.min(chipWidth, gb.width)
    val ch = math.min(chipHeight, gb.height)

    // To avoid thin chips on the right/bottom borders merge to left/top
    val chipCols: Int = gb.width / cw
    val chipRows: Int = gb.height / ch

    for {
      col <- Iterator.range(start = 0, end = chipCols)
      row <- Iterator.range(start = 0, end = chipRows)
    } yield {
      bufferGrid(GridBounds(
        colMin = col * cw,
        rowMin = row * cw,
        colMax = if (col == chipCols - 1) gb.colMax else col * cw + cw - 1,
        rowMax = if (row == chipRows - 1) gb.rowMax else row * ch + ch - 1
      ))
    }
  }

  def getSizeFromURI(uri: URI, s3Client: S3Client): Long = {
    val amazonURI = new AmazonS3URI(uri)
    val obj = s3Client.getObject(amazonURI.getBucket, amazonURI.getKey)
    obj.getObjectMetadata.getContentLength
  }

  /** We need to suppress this warning because there's a perfectly safe `head` call being
    *  made here. The compiler just isn't smart enough to figure that out
    *
    *  @param layer An ingest layer specification
    */
  @SuppressWarnings(Array("TraversableHead"))
  def ingestLayer(params: CommandLine.Params)(layer: IngestLayer)(implicit sc: SparkContext) = {
    val resampleMethod = layer.output.resampleMethod
    val tileSize = layer.output.tileSize
    val destCRS = layer.output.crs
    val ndPattern = layer.output.ndPattern
    val bandCount: Int = layer.sources.map(_.bandMaps.map(_.target).max).max
    val layoutScheme = ZoomedLayoutScheme(destCRS, tileSize)
    val s3Client = S3Client.DEFAULT
    val repartitionSize =
      layer.sources.map { s =>
        // Convert partitionsSize from megabytes to bytes
        math.max(params.partitionsPerFile, getSizeFromURI(s.uri, s3Client) / (params.partitionsSize * 1024 * 1024))
      }.sum.toInt

    // Read source tiles and reproject them to desired CRS
    val sourceTiles: RDD[((ProjectedExtent, Int), Tile)] =
      sc.parallelize(layer.sources, layer.sources.length)
        .flatMap ({ source =>
          val geotiff = MultibandGeoTiff(
            byteReader = uriRangReader(source.uri),
            decompress = false,
            streaming = true
          )

          gridBoundChips(geotiff.tile.gridBounds, params.windowSize, params.windowSize)
            .map { chipBounds => (source, geotiff.rasterExtent.extentFor(chipBounds)) }
        })
        .repartition(repartitionSize)
        .flatMap { case (source, chipExtent) =>
          val geotiff = MultibandGeoTiff(
            byteReader = uriRangReader(source.uri),
            e = Some(chipExtent)
          )

          val chip = geotiff.tile

          // Set NoData values if a pattern has been specified
          val maskedChip = ndPattern.fold(chip)(mask => mask(chip))

          source.bandMaps.map { bm: BandMapping =>
            // GeoTrellis multi-band tiles are 0 indexed
            val band = maskedChip.band(bm.source - 1).reproject(chipExtent, geotiff.crs, destCRS)
            (ProjectedExtent(band.extent, destCRS), bm.target - 1) -> band.tile
          }
        }

    val (maxZoom, tileLayerMetadata) = Ingest.calculateTileLayerMetadata(layer, layoutScheme)

    val tiledRdd = sourceTiles.tileToLayout[(SpatialKey, Int)](
      tileLayerMetadata.cellType,
      tileLayerMetadata.layout,
      resampleMethod)

    // Merge Tiles into MultibandTile and fill in bands that aren't listed
    val multibandTiledRdd: RDD[(SpatialKey, MultibandTile)] = tiledRdd
      .map { case ((key, band), tile) => key -> (tile, band) }
      .groupByKey
      .map { case (key, tiles) =>
        val prototype: Tile = tiles.head._1
        val emptyTile: Tile = ArrayTile.empty(prototype.cellType, prototype.cols, prototype.rows)
        val arr = tiles.toArray
        val bands: Seq[Tile] =
          for (band <- 0 until bandCount) yield
            arr.find(_._2 == band).map(_._1).getOrElse(emptyTile)
        key -> MultibandTile(bands)
      }

    val layerRdd = ContextRDD(multibandTiledRdd, tileLayerMetadata)
    val (writer, deleter, attributeStore) = getRfLayerManagement(layer.output)

    val sharedId = LayerId(layer.id.toString, 0)
    val failsafeDeleteLayer = deleteLayer(deleter)(_)

    if (params.overwrite && attributeStore.layerExists(sharedId)) { failsafeDeleteLayer(sharedId) }

    logger.info("Writing layers")
    attributeStore.write(sharedId, "ingestComplete", false)
    if (layer.output.pyramid) { // If pyramiding
      Pyramid.upLevels(layerRdd, layoutScheme, maxZoom, 1, resampleMethod) { (rdd, zoom) =>
        logger.info(s"Writing zoom level $zoom in ${layer.id.toString}")
        val layerId = LayerId(layer.id.toString, zoom)
        if (params.overwrite && attributeStore.layerExists(layerId)) { failsafeDeleteLayer(layerId) }
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

    implicit val sc = new SparkContext(conf)

    implicit def asS3Payload(status: IngestStatus): String = S3IngestStatus(sceneId, status).asJson.noSpaces

    try {
      ingestDefinition.layers.foreach(ingestLayer(params))
      if (params.testRun) ingestDefinition.layers.foreach(Validation.validateCatalogEntry)
      putObject(
        params.statusBucket,
        ingestDefinition.id.toString,
        IngestStatus.Ingested
      )
    } catch {
      case t: Throwable =>
        logger.error(t.stackTraceString)
        putObject(
          params.statusBucket,
          ingestDefinition.id.toString,
          IngestStatus.Failed
        )
    } finally {
      sc.stop
    }
  }
}
