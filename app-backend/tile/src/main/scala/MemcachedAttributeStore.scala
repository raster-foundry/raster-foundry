package com.azavea.rf.tile

import geotrellis.spark._
import geotrellis.spark.io._

import spray.json._
import scalacache._

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scalacache.caffeine.CaffeineCache
import scalacache.memcached.MemcachedCache
import com.github.benmanes.caffeine.cache._
import com.typesafe.scalalogging.LazyLogging

/** Stores attributes in memcached as strings
  *
  * @usecase The standard GeoTrellis [[AttributeStore]]s don't allow easy modification
  *           of caching mechanism. This mixin trait overwrites appropriate fields
  *           to provide caching backed by Memcached.
  * @note    Certain calls (e.g. to list layerIds) which are not cached by default in a
  *           GeoTrellis [[AttributeStore]] won't be cached with this trait. Further
  *           changes will be necessary for this behaior.
  */
trait MemcachedAttributeStore extends AttributeStore with LazyLogging {
  /** Implicit necessary for scalacache writing/reading - must be provided when mixed in */
  implicit val cache: ScalaCache[Array[Byte]]

  // Configuration Flags (modify when mixing in)

  /** Whether or not to cache all layer attributes  */
  def cacheAll = true

  /** Time to live for cached elements */
  def cacheTTL = Some(10.minutes)

  /** Timeout on future for cache retrieval */
  def timeout = 30.seconds

  // End Configuration Flags

  def memcachedRead[T: JsonFormat](layerId: LayerId, attributeName: String): T = {
    val futureJson = get[String, Array[Byte]](s"$layerId-$attributeName")
      .map { _.getOrElse(super.read[T](layerId, attributeName).toJson.compactPrint) }
    val json = Await.result(futureJson, timeout)
    logger.debug(s"Memcached read of $layerId-$attributeName")
    json.parseJson.convertTo[T]
  }

  def memcachedWrite[T: JsonFormat](layerId: LayerId, attributeName: String, value: T): Unit = {
    logger.debug(s"Memcached write of $layerId-$attributeName")
    put[String, Array[Byte]](s"$layerId-$attributeName")(value.toJson.compactPrint, ttl = cacheTTL)
  }

  abstract override def write[T: JsonFormat](layerId: LayerId, attributeName: String, value: T): Unit = {
    super.write[T](layerId, attributeName, value)
    if (cacheAll) memcachedWrite(layerId, attributeName, value)
  }

  abstract override def read[T: JsonFormat](layerId: LayerId, attributeName: String): T = {
    if (cacheAll) {
      memcachedRead[T](layerId, attributeName)
    } else {
      super.read[T](layerId, attributeName)
    }
  }

  override def cacheRead[T: JsonFormat](layerId: LayerId, attributeName: String): T = {
    memcachedRead[T](layerId, attributeName)
  }

  override def cacheWrite[T: JsonFormat](layerId: LayerId, attributeName: String, value: T): Unit = {
    super.write[T](layerId, attributeName, value)
    memcachedWrite(layerId, attributeName, value)
  }

  override def clearCache(): Unit =
    removeAll()

  override def clearCache(id: LayerId): Unit =
    removeAll()

  override def clearCache(layerId: LayerId, attributeName: String): Unit =
    remove(s"$layerId-$attributeName")
}

