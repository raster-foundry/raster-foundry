package geotrellis.spark.io.postgres

import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.tables._
import com.azavea.rf.datamodel.LayerAttribute

import geotrellis.spark.io.DiscreteLayerAttributeStore
import geotrellis.spark.LayerId
import spray.json._
import io.circe.parser._
import cats.implicits._

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration

object PostgresAttributeStore {
  def apply(attributeTable: String = "layer_attributes")(implicit database: DB, ec: ExecutionContext): PostgresAttributeStore =
    new PostgresAttributeStore(attributeTable)
}

/** A blocking GeoTrellis attribute store */
class PostgresAttributeStore(val attributeTable: String = "layer_attributes")(implicit val database: DB, ec: ExecutionContext) extends DiscreteLayerAttributeStore {
  def read[T: JsonFormat](layerId: LayerId, attributeName: String): T =
    Await.result(LayerAttributes.read(layerId.name, layerId.zoom, attributeName).map { opt =>
      opt
        .map(_.value)
        .getOrElse(throw new Exception(s"No attribute: $layerId, $attributeName"))
        .noSpaces
        .parseJson
        .convertTo[T]
    }, Duration.Inf)

  def readAll[T: JsonFormat](attributeName: String): Map[LayerId, T] =
    Await.result(LayerAttributes.readAll(attributeName).map { _.map { la =>
      la.layerId -> la.value.noSpaces.parseJson.convertTo[T]
    }.toMap }, Duration.Inf)

  def write[T: JsonFormat](layerId: LayerId, attributeName: String, value: T): Unit =
    Await.result(LayerAttributes.insertLayerAttribute(
      LayerAttribute(
        layerName = layerId.name,
        zoom = layerId.zoom,
        name = attributeName,
        value = parse(value.toJson.toString).valueOr(throw _)
      )
    ), Duration.Inf)

  def layerExists(layerId: LayerId): Boolean =
    Await.result(LayerAttributes.layerExists(layerId.name, layerId.zoom), Duration.Inf)

  def delete(layerId: LayerId): Unit =
    Await.result(LayerAttributes.delete(layerId.name, layerId.zoom), Duration.Inf)

  def delete(layerId: LayerId, attributeName: String): Unit =
    Await.result(LayerAttributes.delete(layerId.name, layerId.zoom, attributeName), Duration.Inf)

  def layerIds: Seq[LayerId] =
    Await.result(LayerAttributes.layerIds.map(_.map { case (name, zoom) => LayerId(name, zoom) }.toSeq), Duration.Inf)

  def availableAttributes(id: LayerId): Seq[String] =
    Await.result(LayerAttributes.availableAttributes(id.name, id.zoom).map(_.toSeq), Duration.Inf)
}
