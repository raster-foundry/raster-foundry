package geotrellis.spark.io.postgres

import java.util.concurrent.Executors

import cats.implicits._
import com.azavea.rf.common.Config
import com.azavea.rf.database.tables._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.datamodel.LayerAttribute
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster.io.json._
import geotrellis.spark.LayerId
import geotrellis.spark.io.DiscreteLayerAttributeStore
import io.circe.parser._
import spray.json._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}

object PostgresAttributeStoreThreadPool {
  implicit lazy val ec: ExecutionContext =
    ExecutionContext.fromExecutor(
      Executors.newFixedThreadPool(
        Config.geotrellis.postgresAttributeStoreThreads,
        new ThreadFactoryBuilder().setNameFormat("gt-postgres-attrstore-%d").build()
      )
    )
}

object PostgresAttributeStore {
  def apply(attributeTable: String = "layer_attributes")(implicit database: DB): PostgresAttributeStore =
    new PostgresAttributeStore(attributeTable)
}

/** A blocking GeoTrellis attribute store */
class PostgresAttributeStore(val attributeTable: String = "layer_attributes")(implicit val database: DB)
  extends DiscreteLayerAttributeStore with HistogramJsonFormats with LazyLogging {

  import PostgresAttributeStoreThreadPool._

  val awaitTimeout: FiniteDuration = Config.geotrellis.postgresAttributeStoreTimeout

  def read[T: JsonFormat](layerId: LayerId, attributeName: String): T = {
    logger.debug(s"read($layerId-$attributeName)")
    Await.result(LayerAttributes.read(layerId.name, layerId.zoom, attributeName).map { opt =>
      opt
        .map(_.value)
        .getOrElse(throw new Exception(s"No attribute: $layerId, $attributeName"))
        .noSpaces
        .parseJson
        .convertTo[T]
    }, awaitTimeout)
  }

  def readAll[T: JsonFormat](attributeName: String): Map[LayerId, T] = {
    logger.debug(s"readAll($attributeName)")
    Await.result(LayerAttributes.readAll(attributeName).map {
      _.map { la =>
        la.layerId -> la.value.noSpaces.parseJson.convertTo[T]
      }.toMap
    }, awaitTimeout)
  }

  def write[T: JsonFormat](layerId: LayerId, attributeName: String, value: T): Unit = {
    logger.debug(s"write($layerId-$attributeName)")
    Await.result(LayerAttributes.insertLayerAttribute(
      LayerAttribute(
        layerName = layerId.name,
        zoom = layerId.zoom,
        name = attributeName,
        value = parse(value.toJson.toString).valueOr(throw _)
      )
    ), awaitTimeout)
  }

  def getHistogram[T: JsonFormat](layerId: LayerId): Future[Option[T]] = {
    logger.debug(s"getHistogram($layerId)")
    LayerAttributes.read(layerId.name, layerId.zoom, "histogram").map { opt =>
      Option(opt
        .map(_.value)
        .getOrElse(throw new Exception(s"No attribute: $layerId, Histogram"))
        .noSpaces
        .parseJson
        .convertTo[T])
    }
  }

  def layerExists(layerId: LayerId): Boolean = {
    logger.debug(s"layerExists($layerId)")
    Await.result(LayerAttributes.layerExists(layerId.name, layerId.zoom), awaitTimeout)
  }

  def delete(layerId: LayerId): Unit = {
    logger.debug(s"delete($layerId)")
    Await.result(LayerAttributes.delete(layerId.name, layerId.zoom), awaitTimeout)
  }

  def delete(layerId: LayerId, attributeName: String): Unit = {
    logger.debug(s"delete($layerId-$attributeName)")
    Await.result(LayerAttributes.delete(layerId.name, layerId.zoom, attributeName), awaitTimeout)
  }

  def layerIds: Seq[LayerId] = {
    logger.debug(s"layerIds($layerIds)")
    Await.result(LayerAttributes.layerIds.map(_.map { case (name, zoom) => LayerId(name, zoom) }.toSeq), awaitTimeout)
  }

  def layerIds(layerNames: Set[String]): Seq[LayerId] = {
    logger.debug(s"layerIdsWithLayerNames($layerNames)")
    Await.result(LayerAttributes.layerIds(layerNames).map(
      _.map { case (name, zoom) => LayerId(name, zoom) }.toSeq), awaitTimeout)
  }

  def maxZoomsForLayers(layerNames: Set[String]): Future[Option[Map[String, Int]]] = {
    logger.debug(s"maxZoomsForLayers($layerNames)")
    LayerAttributes.maxZoomsForLayers(layerNames).map(x => Option(x.toMap))
  }

  def availableAttributes(id: LayerId): Seq[String] = {
    logger.debug(s"availableAttributes($id")
    Await.result(LayerAttributes.availableAttributes(id.name, id.zoom).map(_.toSeq), awaitTimeout)
  }
}
