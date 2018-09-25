package com.azavea.rf.database

import cats.implicits._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel.LayerAttribute
import doobie.Fragments._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import geotrellis.spark.LayerId

object LayerAttributeDao extends Dao[LayerAttribute] {

  val tableName = "layer_attributes"

  def selectF: Fragment = fr"""
      SELECT
        layer_name, zoom, name, value
      FROM
    """ ++ tableF

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
