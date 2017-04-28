package com.azavea.rf.batch.util

import com.azavea.rf.batch.ingest.model.IngestLayer

import geotrellis.spark._
import geotrellis.spark.io.file._
import geotrellis.spark.io.s3._
import spray.json.DefaultJsonProtocol._
import com.typesafe.scalalogging.LazyLogging

import java.net.URI
import java.security.InvalidParameterException
import java.util.UUID

object Validation extends LazyLogging {

  /** Check that an ingested layer has a valid s3-based catalog entry for an ingest definition */
  def validateS3CatalogEntry(catalogURI: URI, layerID: UUID): Unit = {
    val (s3Bucket, s3Key) = S3.parse(catalogURI)
    val attributeStore = S3AttributeStore(s3Bucket, s3Key)
    val gtLayerID = LayerId(layerID.toString, 0)
    if (!attributeStore.read[Boolean](gtLayerID, "ingestComplete"))
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
    case scheme =>
      throw new InvalidParameterException(s"Unable to validate catalog for scheme: $scheme")
  }
}

