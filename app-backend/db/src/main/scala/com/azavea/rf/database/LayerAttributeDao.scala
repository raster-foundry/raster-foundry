package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel.LayerAttribute
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.Fragments._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import java.util.UUID

import geotrellis.spark.LayerId

import scala.concurrent.Future


object LayerAttributeDao extends Dao[LayerAttribute] {

  val tableName = "layer_attributes"

  def selectF = fr"""
      SELECT
        layer_name, zoom, name, value
      FROM
    """ ++ tableF

  def unsafeGetAttribute(layerId: LayerId, attributeName: String)(implicit xa: Transactor[IO]): ConnectionIO[LayerAttribute] = {
    query
      .filter(fr"name = ${attributeName}")
      .filter(fr"zoom = ${layerId.zoom}")
      .filter(fr"layer_name = ${layerId.name}")
      .select
  }

  def getAttribute(layerId: LayerId, attributeName: String)(implicit xa: Transactor[IO]): ConnectionIO[Option[LayerAttribute]] = {
    query
      .filter(fr"name = ${attributeName}")
      .filter(fr"zoom = ${layerId.zoom}")
      .filter(fr"layer_name = ${layerId.name}")
      .selectOption
  }

  def listAllAttributes(attributeName: String)(implicit xa: Transactor[IO]): ConnectionIO[List[LayerAttribute]] = {
    query.filter(fr"name = ${attributeName}").list
  }

  def insertLayerAttribute(layerAttribute: LayerAttribute)(implicit xa: Transactor[IO]): ConnectionIO[Int] = {
    // This insert includes conflict handling, because if we re-ingest a scene, its layerattributes should already
    // be in the db.
    val insertStatement = fr"INSERT into" ++ tableF ++
      fr"""
          (layer_name, zoom, name, value)
      VALUES
          (${layerAttribute.layerName}, ${layerAttribute.zoom}, ${layerAttribute.name}, ${layerAttribute.value})
      ON CONFLICT (layer_name, zoom, name) DO UPDATE set value = ${layerAttribute.value}
      """
    insertStatement.update.run
  }

  def layerExists(layerId: LayerId)(implicit xa: Transactor[IO]): ConnectionIO[Boolean] = {
    (fr"SELECT 1 FROM" ++ tableF ++ fr"""
      WHERE layer_name = ${layerId.name} LIMIT 1
    """).query[Int].to[List].map(!_.isEmpty)
  }

  def delete(layerId: LayerId)(implicit xa: Transactor[IO]) = {
    query
      .filter(fr"layer_name = ${layerId.name}")
      .filter(fr"zoom = ${layerId.zoom}")
      .delete
  }

  def delete(layerId: LayerId, attributeName: String)(implicit xa: Transactor[IO]) = {
    query
      .filter(fr"layer_name = ${layerId.name}")
      .filter(fr"zoom = ${layerId.zoom}")
      .filter(fr"name = ${attributeName}")
      .delete
  }

  def layerIds(implicit xa: Transactor[IO]): ConnectionIO[List[(String, Int)]] = {
    (fr"SELECT layer_name, zoom FROM" ++ tableF)
      .query[(String, Int)].to[List]
  }

  def layerIds(layerNames: Set[String])(implicit xa: Transactor[IO]): ConnectionIO[List[(String, Int)]] = {
    val f1 = layerNames.toList.toNel.map(lns => in(fr"layer_name", lns))
                                        (fr"SELECT layer_name, zoom FROM" ++ tableF ++ whereAndOpt(f1))
      .query[(String, Int)].to[List]
  }

  def maxZoomsForLayers(layerNames: Set[String])(implicit xa: Transactor[IO]): ConnectionIO[List[(String, Int)]] = {
    val f1 = layerNames.toList.toNel.map(lns => in(fr"layer_name", lns))
    (fr"SELECT layer_name, COALESCE(MAX(zoom), 0) as zoom FROM" ++ tableF  ++ whereAndOpt(f1) ++ fr"GROUP BY layer_name")
      .query[(String, Int)]
      .to[List]
  }

  def availableAttributes(layerId: LayerId)(implicit xa: Transactor[IO]): ConnectionIO[List[String]] = {
    val f1 = fr"layer_name = ${layerId.name}"
    val f2 = fr"zoom = ${layerId.zoom}"
    (fr"SELECT name FROM" ++ tableF ++ whereAnd(f1, f2)).query[String].to[List]
  }
}

