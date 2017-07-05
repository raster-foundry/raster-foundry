package com.azavea.rf.batch.migration

import com.azavea.rf.batch.Job
import com.azavea.rf.batch.ingest.model._
import com.azavea.rf.database.{Database => DB}

import geotrellis.proj4.CRS
import geotrellis.raster.io._
import geotrellis.raster.histogram._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.LayerAttributes
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.spark.io.s3._
import geotrellis.vector._
import spray.json.DefaultJsonProtocol._
import com.amazonaws.services.s3.AmazonS3URI

import java.net.URI

case class S3ToPostgres(uri: URI, attributeTable: String = "layer_attributes")(implicit val database: DB) extends Job {
  val name = S3ToPostgres.name

  def run: Unit = {
    val s3Uri = new AmazonS3URI(uri)
    val from = S3AttributeStore(s3Uri.getBucket, s3Uri.getKey)
    val to = PostgresAttributeStore(attributeTable)

    from.layerIds.foreach { layerId =>
      if(layerId.zoom > 0) {
        val LayerAttributes(header, metadata, keyIndex, schema) = from.readLayerAttributes[S3LayerHeader, TileLayerMetadata[SpatialKey], SpatialKey](layerId)
        to.write(layerId, AttributeStore.Fields.header, header)
        to.write(layerId, AttributeStore.Fields.metadata, metadata)
        to.write(layerId, AttributeStore.Fields.keyIndex, keyIndex)
        to.write(layerId, AttributeStore.Fields.schema, schema)
      } else {
        to.write(layerId, "histogram", from.read[Array[Histogram[Double]]](layerId, "histogram"))
        to.write(layerId, "extent", from.read[Extent](layerId, "extent")(ExtentJsonFormat))(ExtentJsonFormat)
        to.write(layerId, "crs", from.read[CRS](layerId, "crs")(CRSJsonFormat))(CRSJsonFormat)
        to.write(layerId, "layerComplete", from.read[Boolean](layerId, "layerComplete"))
      }
    }
  }
}

object S3ToPostgres {
  val name = "migration_s3_postgres"

  def main(args: Array[String]): Unit = {
    implicit val db = DB.DEFAULT

    val job = args.toList match {
      case List(uri, attributeTable) => S3ToPostgres(new URI(uri), attributeTable)
      case List(uri) => S3ToPostgres(new URI(uri))
      case _ => throw new Exception("No URI passed for migration job")
    }

    job.run
  }
}
