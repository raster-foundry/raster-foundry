package com.azavea.rf



import java.util.UUID
import java.sql.Timestamp
import java.time.Instant

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import spray.json._

import com.azavea.rf.datamodel.latest.schema.tables.UsersRow
import com.azavea.rf.utils.PaginatedResponse


/**
  * Json formats for user
  */
package object user extends SprayJsonSupport with DefaultJsonProtocol {

  implicit object UuidJsonFormat extends RootJsonFormat[UUID] {
    def write(obj: UUID) = JsString(obj.toString)
    def read(value: JsValue) = value match {
      case JsString(obj) => UUID.fromString(obj)
      case obj           => deserializationError("Expected UUID but got " + obj)
    }
  }

  implicit object TimestampJsonFormat extends RootJsonFormat[Timestamp] {
    def write(obj: Timestamp) = JsString(obj.toInstant().toString())

    def read(json: JsValue) = json match {
      case JsString(time) => Timestamp.from(Instant.parse(time))
      case _ => throw new DeserializationException("Expected ISO 8601 Date but got " + json)
    }
  }

  implicit val userFormat = jsonFormat3(UsersRow)
  implicit val userCreateFormat = jsonFormat2(UsersRowCreate)
  implicit val paginatedUserFormat = jsonFormat6(PaginatedResponse[UsersRow])
}
