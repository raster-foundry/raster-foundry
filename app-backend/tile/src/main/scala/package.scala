package com.azavea.rf

import java.util.UUID

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.{ContentType, HttpEntity, MediaTypes}
import com.github.blemale.scaffeine.{Cache => ScaffeineCache}
import geotrellis.raster.io.geotiff.MultibandGeoTiff
import geotrellis.raster.render.Png
import spray.json._

package object tile {
  implicit object UUIDJsonFormat extends RootJsonFormat[UUID] {
    def write(uuid: UUID): JsValue = JsString(uuid.toString)
    def read(js: JsValue): UUID = js match {
      case JsString(uuid) => UUID.fromString(uuid)
      case _ =>
        deserializationError(s"Failed to parse UUID string ${js} to java UUID")
    }
  }

  implicit class withLayerCacheMethods[K, V](cache: ScaffeineCache[K, V]) extends Config {
    def take(key: K, mappingFunction: K => V): V =
      if (withCaching) cache.get(key, mappingFunction)
      else mappingFunction(key)
  }

  implicit val pngMarshaller: ToEntityMarshaller[Png] = {
    val contentType = ContentType(MediaTypes.`image/png`)
    Marshaller.withFixedContentType(contentType) { png ⇒ HttpEntity(contentType, png.bytes) }
  }

  implicit val tiffMarshaller: ToEntityMarshaller[MultibandGeoTiff] = {
    val contentType = ContentType(MediaTypes.`image/tiff`)
    Marshaller.withFixedContentType(contentType) {
      tiff => HttpEntity(contentType, tiff.toByteArray)
    }
  }
}