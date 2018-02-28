package geotrellis.spark.io.postgres

import java.util.concurrent.Executors

import cats.effect.IO
import cats.implicits._
import com.azavea.rf.common.Config
import com.azavea.rf.database.LayerAttributeDao
import com.azavea.rf.datamodel.LayerAttribute
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.scalalogging.LazyLogging
import doobie.util.transactor.Transactor
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._
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
  def apply(attributeTable: String = "layer_attributes")(implicit xa: Transactor[IO]): PostgresAttributeStore =
    new PostgresAttributeStore(attributeTable)
}

/** A blocking GeoTrellis attribute store */
class PostgresAttributeStore(val attributeTable: String = "layer_attributes")(implicit xa: Transactor[IO])
  extends DiscreteLayerAttributeStore with HistogramJsonFormats with LazyLogging {

  import PostgresAttributeStoreThreadPool._

  val awaitTimeout: FiniteDuration = Config.geotrellis.postgresAttributeStoreTimeout

  def read[T: JsonFormat](layerId: LayerId, attributeName: String): T = {
    logger.debug(s"read($layerId-$attributeName)")
    Await.result(LayerAttributeDao.getAttribute(layerId, attributeName).transact(xa).unsafeToFuture
      .map(_.value.noSpaces.parseJson.convertTo[T]), awaitTimeout)
  }

  def readAll[T: JsonFormat](attributeName: String): Map[LayerId, T] = {
    logger.debug(s"readAll($attributeName)")
    Await.result(
      LayerAttributeDao.getAllAttributes(attributeName).transact(xa).unsafeToFuture.map {
        _.map { attribute =>
          attribute.layerId -> attribute.value.noSpaces.parseJson.convertTo[T]
        }.toMap
      }, awaitTimeout
    )
  }

  def write[T: JsonFormat](layerId: LayerId, attributeName: String, value: T): Unit = {
    logger.debug(s"write($layerId-$attributeName)")
    Await.result(LayerAttributeDao.insertLayerAttribute(
      LayerAttribute(
        layerName = layerId.name,
        zoom = layerId.zoom,
        name = attributeName,
        value = parse(value.toJson.toString).valueOr(throw _)
      )
    ).transact(xa).unsafeToFuture, awaitTimeout)
  }

  def getHistogram[T: JsonFormat](layerId: LayerId): Future[Option[T]] = {
    logger.debug(s"getHistogram($layerId)")
    LayerAttributeDao.getAttribute(layerId, "histogram").transact(xa).unsafeToFuture
      .map { attribute =>
        Option(attribute.value.noSpaces.parseJson.convertTo[T])
      }
  }

  def layerExists(layerId: LayerId): Boolean = {
    logger.debug(s"layerExists($layerId)")
    Await.result(LayerAttributeDao.layerExists(layerId).transact(xa).unsafeToFuture, awaitTimeout)
  }

  def delete(layerId: LayerId): Unit = {
    logger.debug(s"delete($layerId)")
    Await.result(LayerAttributeDao.delete(layerId).transact(xa).unsafeToFuture, awaitTimeout)
  }

  def delete(layerId: LayerId, attributeName: String): Unit = {
    logger.debug(s"delete($layerId-$attributeName)")
    Await.result(LayerAttributeDao.delete(layerId, attributeName).transact(xa).unsafeToFuture, awaitTimeout)
  }

  def layerIds: Seq[LayerId] = {
    logger.debug(s"layerIds($layerIds)")
    Await.result(
      LayerAttributeDao.layerIds.transact(xa).unsafeToFuture
        .map(_.map { case (name, zoom) => LayerId(name, zoom) }.toSeq),
      awaitTimeout
    )
  }

  def layerIds(layerNames: Set[String]): Seq[LayerId] = {
    logger.debug(s"layerIdsWithLayerNames($layerNames)")
    Await.result(
      LayerAttributeDao.layerIds(layerNames).transact(xa).unsafeToFuture
        .map { _.map { case (name, zoom) => LayerId(name, zoom) }.toSeq },
      awaitTimeout
    )
  }

  def maxZoomsForLayers(layerNames: Set[String]): Future[Option[Map[String, Int]]] = {
    logger.debug(s"maxZoomsForLayers($layerNames)")
    LayerAttributeDao.maxZoomsForLayers(layerNames).transact(xa).unsafeToFuture
      .map {
        case seq if seq.length > 0 => seq.toMap.some
        case _ => None
      }
  }

  def availableAttributes(id: LayerId): Seq[String] = {
    logger.debug(s"availableAttributes($id")
    Await.result(
      LayerAttributeDao.availableAttributes(id).transact(xa).unsafeToFuture
        .map(_.toSeq),
      awaitTimeout
    )
  }
}
