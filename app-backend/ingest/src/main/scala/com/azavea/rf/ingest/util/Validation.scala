package com.azavea.rf.ingest.util

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
import geotrellis.proj4.LatLng
import com.typesafe.scalalogging.LazyLogging
import spray.json._
import DefaultJsonProtocol._

import java.net.URI
import java.util.UUID

import com.azavea.rf.ingest.model._

object Validation extends LazyLogging {

  /** Check that an ingested layer has a valid s3-based catalog entry for an ingest definition */
  def validateS3CatalogEntry(catalogURI: URI, layerID: UUID): Unit = {
    val (s3Bucket, s3Key) = S3.parse(catalogURI)
    val attributeStore = S3AttributeStore(s3Bucket, s3Key)
    val gtLayerID = LayerId(layerID.toString, 0)
    if (! attributeStore.read[Boolean](gtLayerID, "ingestComplete"))
      throw new Exception("Something went wrong during ingest...")
    else logger.info("Ingest completed successfully")
  }

  /** Check that an ingested layer has a valid file-based catalog entry for an ingest definition */
  def validateFileCatalogEntry(catalogURI: URI, layerID: UUID): Unit = {
    val attributeStore = FileAttributeStore(catalogURI.getPath)
    val gtLayerID = LayerId(layerID.toString, 0)

    if (! attributeStore.read[Boolean](gtLayerID, "ingestComplete"))
      throw new Exception("Something went wrong during ingest...")
    else logger.info("Ingest completed successfully")
  }

  /** Check that an ingested layer has a valid catalog entry for an ingest definition */
  def validateCatalogEntry(layer: IngestLayer): Unit =
    validateCatalogEntry(layer.output.uri, layer.id)

  /** Check that an ingested layer has a valid catalog entry for an ingest definition */
  def validateCatalogEntry(catalogURI: URI, layerID: UUID): Unit = catalogURI.getScheme match {
    case "s3" | "s3a" | "s3n" =>
      validateS3CatalogEntry(catalogURI, layerID)
    case "file" =>
      validateFileCatalogEntry(catalogURI, layerID)
  }

}

