package com.azavea.rf.batch.migration

import cats.effect.IO
import com.azavea.rf.batch._
import com.azavea.rf.batch.util._
import com.azavea.rf.batch.ingest.model._
import geotrellis.proj4.CRS
import geotrellis.raster.io._
import geotrellis.raster.histogram._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.LayerAttributes
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.spark.io.s3._
import geotrellis.vector._
import com.amazonaws.services.s3.AmazonS3URI
import com.azavea.rf.database.util.RFTransactor
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import doobie.util.transactor.Transactor
import spray.json.DefaultJsonProtocol._

import scala.concurrent.Future
import scala.util.{Failure, Success}

case class S3ToPostgres(uri: AmazonS3URI, attributeTable: String = "layer_attributes", layerName: Option[String] = None)(implicit val xa: Transactor[IO]) extends Job {
  val name = S3ToPostgres.name

  private implicit val cache: Cache[(LayerId, String), Any] = Scaffeine().softValues().build()

  def run: Unit = {
    val (from, to) = S3AttributeStore(uri.getBucket, uri.getKey) -> PostgresAttributeStore(attributeTable)

    Future
      .sequence(layerName.fold(from.layerIds)(name => from.layerIds.filter(_.name == name))
        .map { layerId => Future {
          logger.info(s"Processing layer: $layerId...")
          try {
            if (layerId.zoom > 0) {
              val LayerAttributes(header, metadata, keyIndex, schema) = from.readLayerAttributesSafe[S3LayerHeader, TileLayerMetadata[SpatialKey], SpatialKey](layerId)
              to.write(layerId, AttributeStore.Fields.header, header)
              to.write(layerId, AttributeStore.Fields.metadata, metadata)
              to.write(layerId, AttributeStore.Fields.keyIndex, keyIndex)
              to.write(layerId, AttributeStore.Fields.schema, schema)
              to.write(layerId, "layerComplete", from.cacheReadSafe[Boolean](layerId, "layerComplete"))
            } else {
              to.write(layerId, "histogram", from.cacheReadSafe[Array[Histogram[Double]]](layerId, "histogram"))
              to.write(layerId, "extent", from.cacheReadSafe[Extent](layerId, "extent")(ExtentJsonFormat, cache))(ExtentJsonFormat)
              to.write(layerId, "crs", from.cacheReadSafe[CRS](layerId, "crs")(CRSJsonFormat, cache))(CRSJsonFormat)
            }
            layerId
          } catch {
            case th: Throwable => {
              logger.error(s"Missing fields for $layerId. Skipping...")
              logger.error(th.stackTraceString)
              sendError(th)
              layerId
            }
          }
        }}
      ) map { _.map(_.name).distinct.length } onComplete {
      case Success(count) => {
        logger.info(s"Successfully imported scenes (${count}).")
        stop
      }
      case Failure(e) => {
        logger.error(e.stackTraceString)
        sendError(e)
        stop
        sys.exit(1)
      }
    }
  }
}

object S3ToPostgres {
  val name = "migration_s3_postgres"

  def main(args: Array[String]): Unit = {
    implicit val xa = RFTransactor.xa

    val job = args.toList match {
      case List(uri, attributeTable, layerName) => S3ToPostgres(new AmazonS3URI(uri), attributeTable, Some(layerName))
      case List(uri, attributeTable) => S3ToPostgres(new AmazonS3URI(uri), attributeTable)
      case List(uri) => S3ToPostgres(new AmazonS3URI(uri))
      case _ => throw new Exception("No URI passed for migration job")
    }

    job.run
  }
}
