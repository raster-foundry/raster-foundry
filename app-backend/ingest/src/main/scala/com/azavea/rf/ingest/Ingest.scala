package com.azavea.rf.ingest

import com.azavea.rf.ingest.util._
import com.azavea.rf.ingest.tool._

import geotrellis.raster.histogram.Histogram
import geotrellis.raster.io._
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.io.index.ZCurveKeyIndexMethod
import geotrellis.spark.io.s3._
import geotrellis.spark.pyramid.Pyramid
import geotrellis.vector.ProjectedExtent
import geotrellis.raster._
import geotrellis.raster.io.geotiff.MultibandGeoTiff
import geotrellis.spark.tiling._
import geotrellis.proj4._

import org.apache.spark.rdd._
import org.apache.spark._
import spray.json._
import DefaultJsonProtocol._
import java.net.URI

case class BandTile(band: Int, tile: Tile)

object Ingest extends SparkJob {

  case class Params(
    jobDefinition: URI = new URI(""),
    testRun: Boolean = false,
    overwrite: Boolean = false
  )
  type RfLayerWriter = Writer[LayerId, RDD[(SpatialKey, MultibandTile)] with Metadata[TileLayerMetadata[SpatialKey]]]
  type RfLayerDeleter = LayerDeleter[LayerId]

  /** Get a layerwriter and an attribute store for the catalog located at the provided URI
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

  /** Produce metadata for an IngestLayer
    *
    *  @param layer A specification for ingesting a layer
    *  @param scheme A scheme used to construct the tiling grid
    */
  def calculateTileLayerMetadata(layer: IngestLayer, scheme: LayoutScheme): (Int, TileLayerMetadata[SpatialKey]) = {
    // We need to build TileLayerMetadata that we expect to start pyramid from
    val overallExtent = layer.sources
      .map({ src => src.extent.reproject(src.getCRSExtent, layer.output.crs) })
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

  /** We need to supress this warning because there's a perfectly safe `head` call being
    *  made here. The compiler just isn't smart enough to figure that out
    *
    *  @param layer An ingest layer specification
    */
  @SuppressWarnings(Array("TraversableHead"))
  def ingestLayer(overwriteLayer: Boolean)(layer: IngestLayer)(implicit sc: SparkContext) = {
    val resampleMethod = layer.output.resampleMethod
    val tileSize = layer.output.tileSize
    val destCRS = layer.output.crs
    val bandCount: Int = layer.sources.map(_.bandMaps.map(_.target).max).max
    val layoutScheme = ZoomedLayoutScheme(destCRS, tileSize)

    // Read source tiles and reproject them to desired CRS
    val sourceTiles: RDD[((ProjectedExtent, Int), Tile)] =
      sc.parallelize(layer.sources, layer.sources.length).flatMap { source =>
        val tiffBytes = readBytes(source.uri)
        val MultibandGeoTiff(mbTile, srcExtent, srcCRS, _, _) = MultibandGeoTiff(tiffBytes)

        source.bandMaps.map { bm: BandMapping =>
          // GeoTrellis multi-band tiles are 0 indexed
          val band = mbTile.band(bm.source - 1).reproject(srcExtent, srcCRS, destCRS)
          (ProjectedExtent(band.extent, destCRS), bm.target - 1) -> band.tile
        }
      }.split(512, 512) // TODO: Figure out what to do about this split

    val (maxZoom, tileLayerMetadata) = Ingest.calculateTileLayerMetadata(layer, layoutScheme)

    val tiledRdd = sourceTiles.tileToLayout[(SpatialKey, Int)](
      tileLayerMetadata.cellType,
      tileLayerMetadata.layout,
      resampleMethod)

    // Merge Tiles into MultibandTile and fill in bands that aren't listed
    val multibandTiledRdd: RDD[(SpatialKey, MultibandTile)] = tiledRdd
      .map { case ((key, band), tile) => key ->(tile, band) }
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

    if (overwriteLayer) {
      try {
        deleter.delete(sharedId)
        attributeStore.delete(sharedId)
      } catch {
        case e: Throwable => throw e
      }
    }

    if (layer.output.pyramid) { // If pyramiding
      Pyramid.upLevels(layerRdd, layoutScheme, maxZoom, 1, resampleMethod) { (rdd, zoom) =>
        // attributes that apply to all layers are placed at zoom 0
        val layerId = LayerId(layer.id.toString, zoom)

        try {
          if (overwriteLayer) { deleter.delete(layerId) }
          writer.write(layerId, rdd)

          if (zoom == math.max(maxZoom / 2, 1)) {
            import spray.json.DefaultJsonProtocol._
            attributeStore.write(sharedId, "histogram", multibandHistogram(rdd, numBuckets = 256))
          }

          if (zoom == 1) {
            attributeStore.write(sharedId, "extent", rdd.metadata.extent)(ExtentJsonFormat) // avoid using default JF
            attributeStore.write(sharedId, "crs", rdd.metadata.crs)(CRSJsonFormat) // avoid using default JF
          }
          // Write an attribute for verification of completed ingest
          attributeStore.write(sharedId, "ingestComplete", true)
        } catch {
          case e: Throwable =>
            attributeStore.write(sharedId, "ingestComplete", false)
        }
      }
    } else { // If not pyramiding. TODO: figure out exactly what we want to store here
      try {
        if (overwriteLayer) { deleter.delete(sharedId) }
        writer.write(sharedId, layerRdd)
        attributeStore.write(sharedId, "ingestComplete", true)
      } catch {
        case e: Throwable =>
          attributeStore.write(sharedId, "ingestComplete", false)
      }
    }

  }

  /** Sample ingest definitions can be found in the accompanying test/resources
    *
    * @param args Arguments to be parsed by the tooling defined in [[CommandLine]]
    */
  def main(args: Array[String]): Unit = {
    val params = CommandLine.parser.parse(args, Ingest.Params()) match {
      case Some(params) =>
        params
      case None =>
        throw new Exception("Unable to parse command line arguments")
    }

    val ingestDefinition = readString(params.jobDefinition).parseJson.convertTo[IngestDefinition]

    implicit val sc = new SparkContext(conf)

    try {
      ingestDefinition.layers.foreach(ingestLayer(params.overwrite))
      if (params.testRun) ingestDefinition.layers.foreach(Testing.validateCatalogEntry)
    } finally {
      sc.stop
    }
  }
}
