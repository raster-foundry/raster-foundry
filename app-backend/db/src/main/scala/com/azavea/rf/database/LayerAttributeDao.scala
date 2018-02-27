package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.LayerAttribute
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
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

  def getAttribute(layerId: LayerId, attributeName: String)(implicit xa: Transactor[IO]): Future[LayerAttribute] = {
    {
      selectF ++ fr"""
        layer_name = ${layerId.name} AND
        zoom = ${layerId.zoom} AND
        name = ${attributeName}"""
    }
      .query[LayerAttribute]
      .unique
      .transact(xa)
      .unsafeToFuture
  }

  def getAttributeOption(layerId: LayerId, attributeName: String)(implicit xa: Transactor[IO]): Future[Option[LayerAttribute]] = {
    {
      selectF ++ fr"""
        layer_name = ${layerId.name} AND
        zoom = ${layerId.zoom} AND
        name = ${attributeName}"""
    }
      .query[LayerAttribute]
      .option
      .transact(xa)
      .unsafeToFuture
  }

  def getAllAttributes(attributeName: String)(implicit xa: Transactor[IO]): Future[List[LayerAttribute]] = ???

  def insertLayerAttribute(layerAttribute: LayerAttribute)(implicit xa: Transactor[IO]): ConnectionIO[Int] = {
    val insertStatement = fr"INSERT into" ++ tableF ++
      fr"""
          (layer_name, zoom, name, value)
      VALUES
          (${layerAttribute.layerName}, ${layerAttribute.zoom}, ${layerAttribute.name}, ${layerAttribute.value})
      """
    insertStatement.update.run
  }

  def layerExists(layerId: LayerId)(implicit xa: Transactor[IO]): Future[Boolean] = {
    ???
  }

  def delete(layerId: LayerId)(implicit xa: Transactor[IO]) = {
    ???
  }

  def delete(layerId: LayerId, attributeName: String)(implicit xa: Transactor[IO]) = {
    ???
  }

  def layerIds(implicit xa: Transactor[IO]): Future[Iterable[(String, Int)]] = {
    ???
  }

  def layerIds(layerNames: Set[String])(implicit xa: Transactor[IO]): Future[Iterable[(String, Int)]] = {
    ???
  }

  def maxZoomsForLayers(layerNames: Set[String])(implicit xa: Transactor[IO]): Future[Seq[(String, Int)]] = {
    ???
  }

  def availableAttributes(layerId: LayerId)(implicit xa: Transactor[IO]): Future[Iterable[String]] = {
    ???
  }
}

