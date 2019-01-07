package com.rasterfoundry.database

import cats.effect.IO
import cats.implicits._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel.LayerAttribute
import doobie.Fragments._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits._
import geotrellis.spark.LayerId
import spray.json._
import DefaultJsonProtocol._
import java.util.UUID

import geotrellis.raster.histogram.{Histogram, StreamingHistogram}
import io.circe.{Encoder, Json}
import io.circe.parser.parse

case class LayerAttributeDao() {
  def getHistogram[T: JsonFormat](layerId: UUID, xa: Transactor[IO]): IO[T] = {
    LayerAttributeDao
      .unsafeGetAttribute(LayerId(layerId.toString, 0), "histogram")
      .transact(xa)
      .map({ attr =>
        attr.value.noSpaces.parseJson.convertTo[T]
      })
  }

  def getProjectHistogram[T: JsonFormat](projectId: UUID,
                                         xa: Transactor[IO]): IO[List[T]] = {
    LayerAttributeDao
      .getProjectHistogram(projectId)
      .transact(xa)
      .map(_.map({ attr =>
        attr.value.noSpaces.parseJson.convertTo[T]
      }))
  }
}

object LayerAttributeDao extends Dao[LayerAttribute] {

  val tableName = "layer_attributes"

  def selectF: Fragment = fr"""
      SELECT
        layer_name, zoom, name, value
      FROM
    """ ++ tableF

  def getProjectHistogram(
      projectId: UUID): ConnectionIO[List[LayerAttribute]] = {
    query
      .filter(fr"name = 'histogram'")
      .filter(fr"zoom = 0")
      .filter(
        fr"layer_name in (SELECT scene_id :: varchar(255) from scenes_to_projects where project_id = ${projectId})"
      )
      .list
  }

  def unsafeGetAttribute(
      layerId: LayerId,
      attributeName: String): ConnectionIO[LayerAttribute] = {
    query
      .filter(fr"name = ${attributeName}")
      .filter(fr"zoom = ${layerId.zoom}")
      .filter(fr"layer_name = ${layerId.name}")
      .select
  }

  def getAttribute(
      layerId: LayerId,
      attributeName: String): ConnectionIO[Option[LayerAttribute]] = {
    query
      .filter(fr"name = ${attributeName}")
      .filter(fr"zoom = ${layerId.zoom}")
      .filter(fr"layer_name = ${layerId.name}")
      .selectOption
  }

  def listAllAttributes(
      attributeName: String): ConnectionIO[List[LayerAttribute]] = {
    query.filter(fr"name = ${attributeName}").list
  }

  def insertLayerAttribute(
      layerAttribute: LayerAttribute): ConnectionIO[LayerAttribute] = {
    // This insert includes conflict handling, because if we re-ingest a scene, its layerattributes should already
    // be in the db.
    val insertStatement = fr"INSERT into" ++ tableF ++
      fr"""
          (layer_name, zoom, name, value)
      VALUES
          (${layerAttribute.layerName}, ${layerAttribute.zoom}, ${layerAttribute.name}, ${layerAttribute.value})
      ON CONFLICT (layer_name, zoom, name) DO UPDATE set value = ${layerAttribute.value}
      """
    insertStatement.update.withUniqueGeneratedKeys[LayerAttribute]("layer_name",
                                                                   "zoom",
                                                                   "name",
                                                                   "value")
  }

  def layerExists(layerId: LayerId): ConnectionIO[Boolean] = {
    (fr"SELECT 1 FROM" ++ tableF ++ fr"""
      WHERE layer_name = ${layerId.name} LIMIT 1
    """).query[Int].to[List].map(_.nonEmpty)
  }

  def delete(layerId: LayerId): ConnectionIO[Int] = {
    query
      .filter(fr"layer_name = ${layerId.name}")
      .filter(fr"zoom = ${layerId.zoom}")
      .delete
  }

  def delete(layerId: LayerId, attributeName: String): ConnectionIO[Int] = {
    query
      .filter(fr"layer_name = ${layerId.name}")
      .filter(fr"zoom = ${layerId.zoom}")
      .filter(fr"name = ${attributeName}")
      .delete
  }

  def layerIds: ConnectionIO[List[(String, Int)]] = {
    (fr"SELECT layer_name, zoom FROM" ++ tableF)
      .query[(String, Int)]
      .to[List]
  }

  def layerIds(layerNames: Set[String]): ConnectionIO[List[(String, Int)]] = {
    val f1 = layerNames.toList.toNel.map(lns => in(fr"layer_name", lns))
    (fr"SELECT layer_name, zoom FROM" ++ tableF ++ whereAndOpt(f1))
      .query[(String, Int)]
      .to[List]
  }

  def maxZoomsForLayers(
      layerNames: Set[String]): ConnectionIO[List[(String, Int)]] = {
    val f1 = layerNames.toList.toNel.map(lns => in(fr"layer_name", lns))
    (fr"SELECT layer_name, COALESCE(MAX(zoom), 0) as zoom FROM" ++ tableF ++ whereAndOpt(
      f1
    ) ++ fr"GROUP BY layer_name")
      .query[(String, Int)]
      .to[List]
  }

  def unsafeMaxZoomForLayer(layerName: String): ConnectionIO[(String, Int)] = {
    maxZoomsForLayers(Set(layerName)) map {
      case h :: Nil => h
      case _ =>
        throw new Exception(
          s"Several or zero max zooms found for layer $layerName")
    }
  }

  def availableAttributes(layerId: LayerId): ConnectionIO[List[String]] = {
    val f1 = fr"layer_name = ${layerId.name}"
    val f2 = fr"zoom = ${layerId.zoom}"
    (fr"SELECT name FROM" ++ tableF ++ whereAnd(f1, f2)).query[String].to[List]
  }
}
