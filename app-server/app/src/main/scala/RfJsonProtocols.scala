package com.azavea.rf

import java.util.UUID
import java.sql.Timestamp
import java.time.Instant

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

import com.azavea.rf.datamodel.latest.schema.tables.UsersRow
import com.azavea.rf.datamodel.latest.schema.tables.OrganizationsRow

trait RfJsonProtocols extends SprayJsonSupport with DefaultJsonProtocol {

  implicit object UuidJsonFormat extends RootJsonFormat[UUID] {
    def write(uuid: UUID) = JsString(uuid.toString)
    def read(value: JsValue) = value match {
      case JsString(uuid) => UUID.fromString(uuid)
      case _ => deserializationError(s"Expected UUID but got $value")
    }
  }

  implicit object TimestampJsonFormat extends RootJsonFormat[Timestamp] {
    def write(time: Timestamp) = JsString(time.toInstant().toString())

    def read(json: JsValue) = json match {
      case JsString(time) => Timestamp.from(Instant.parse(time))
      case _ => throw new DeserializationException(s"Expected ISO 8601 Date but got $json")
    }
  }

  implicit val userFormat = jsonFormat1(UsersRow)
  implicit val organizationFormat = jsonFormat4(OrganizationsRow)
}
