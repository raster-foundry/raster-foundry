package com.azavea.rf

import spray.json._
import com.github.blemale.scaffeine.{Cache => ScaffeineCache}
import java.util.UUID

package object tile {
  implicit object UUIDJsonFormat extends RootJsonFormat[UUID] {
    def write(uuid: UUID): JsValue = JsString(uuid.toString)
    def read(js: JsValue): UUID = js match {
      case JsString(uuid) => UUID.fromString(uuid)
      case _ =>
        deserializationError(s"Failed to parse UUID string ${js} to java UUID")
    }
  }

  implicit class withLayerCacheMethods[K, V](cache: ScaffeineCache[K, V])
      extends Config {
    def take(key: K, mappingFunction: K => V): V =
      if (withCaching) cache.get(key, mappingFunction)
      else mappingFunction(key)
  }
}
