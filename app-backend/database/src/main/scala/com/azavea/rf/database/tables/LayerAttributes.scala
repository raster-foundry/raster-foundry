package com.azavea.rf.database.tables

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.fields._
import com.azavea.rf.database.query.{ExportQueryParameters, ListQueryResult}
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.params._

import cats.data._
import cats.implicits._
import com.lonelyplanet.akka.http.extensions.PageRequest
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import slick.model.ForeignKeyAction

import java.sql.Timestamp
import java.util.UUID
import java.net.URI

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/** Table that represents GeoTrellis layers metadata
  *
  * LayerAttributes represent asynchronous LayerAttribute queries
  */
class LayerAttributes(_tableTag: Tag) extends Table[LayerAttribute](_tableTag, "layer_attributes") {
  def * = (layerName, zoom, name, value) <> (LayerAttribute.tupled, LayerAttribute.unapply)

  val layerName: Rep[String] = column[String]("layer_name")
  val zoom: Rep[Int] = column[Int]("zoom")
  val name: Rep[String] = column[String]("name")
  val value: Rep[Json] = column[Json]("value")

  def pk = primaryKey("pk_layer_attributes", (layerName, zoom, name))
}

object LayerAttributes extends TableQuery(tag => new LayerAttributes(tag)) with LazyLogging {

  val tq = TableQuery[LayerAttributes]
  type TableQuery = Query[LayerAttributes, LayerAttribute, Seq]

  def read(layerName: String, zoom: Int, name: String)(implicit database: DB): Future[Option[LayerAttribute]] = database.db.run {
    LayerAttributes.filter(r => r.layerName === layerName && r.zoom === zoom && r.name === name).result.headOption
  }

  def readAll(name: String)(implicit database: DB): Future[Iterable[LayerAttribute]] = database.db.run {
    LayerAttributes.filter(_.name === name).result
  }

  def insertLayerAttribute(layerAttribute: LayerAttribute)(implicit database: DB): Future[LayerAttribute] = database.db.run {
    (LayerAttributes returning LayerAttributes).forceInsert(layerAttribute)
  }

  def layerExists(layerName: String, zoom: Int)(implicit database: DB): Future[Boolean] = database.db.run {
    LayerAttributes.filter(r => r.layerName === layerName && r.zoom === zoom).exists.result
  }

  def delete(layerName: String, zoom: Int)(implicit database: DB): Future[Int] = database.db.run {
    LayerAttributes.filter(r => r.layerName === layerName && r.zoom === zoom).delete
  }

  def delete(layerName: String, zoom: Int, name: String)(implicit database: DB): Future[Int] = database.db.run {
    LayerAttributes.filter(r => r.layerName === layerName && r.zoom === zoom && r.name === name).delete
  }

  def layerIds(implicit database: DB): Future[Iterable[(String, Int)]] = database.db.run {
    LayerAttributes.map(r => r.layerName -> r.zoom).result
  }

  def layerIds(layerNames: Set[String])(implicit database: DB): Future[Iterable[(String, Int)]] = database.db.run {
    LayerAttributes
      .filter(_.layerName inSetBind layerNames)
      .map(r => r.layerName -> r.zoom)
      .result
  }

  def maxZoomsForLayers(layerNames: Set[String])(implicit database: DB): Future[Seq[(String, Int)]] = database.db.run {
    LayerAttributes
      .filter(_.layerName inSetBind layerNames)
      .groupBy(_.layerName)
      .map { case (layerId, attrs) => layerId -> attrs.map(_.zoom).max.ifNull(0) }
      .result
  }

  def availableAttributes(layerName: String, zoom: Int)(implicit database: DB): Future[Iterable[String]] = database.db.run {
    LayerAttributes.filter(r => r.layerName === layerName && r.zoom === zoom).map(_.name).result
  }
}
