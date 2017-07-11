package com.azavea.rf

import spray.json._

import java.util.UUID

package object tile {
  implicit object UUIDJsonFormat extends RootJsonFormat[UUID] {
    def write(uuid: UUID): JsValue = JsString(uuid.toString)
    def read(js: JsValue): UUID = js match {
      case JsString(uuid) => UUID.fromString(uuid)
      case _ =>
        deserializationError("Failed to parse UUID string ${js} to java UUID")
    }
  }
}
