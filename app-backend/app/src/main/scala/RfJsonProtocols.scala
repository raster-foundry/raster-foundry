package com.azavea.rf

import java.util.UUID
import java.sql.Timestamp
import java.time.Instant
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

import com.azavea.rf.datamodel._
import com.azavea.rf.database.RFDatabaseJsonProtocol


trait RfJsonProtocols extends SprayJsonSupport
    with DefaultJsonProtocol
    with RFDatabaseJsonProtocol {

  implicit object TimestampJsonFormat extends RootJsonFormat[Timestamp] {
    def write(time: Timestamp) = JsString(time.toInstant().toString())

    def read(json: JsValue) = json match {
      case JsString(time) => Timestamp.from(Instant.parse(time))
      case _ => throw new DeserializationException(s"Expected ISO 8601 Date but got $json")
    }
  }
}
