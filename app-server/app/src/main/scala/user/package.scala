package com.azavea.rf

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport;
import com.azavea.rf.datamodel.latest.schema.tables.UsersRow;

import java.util.UUID;
import java.sql.Timestamp;

import spray.json._

/**
  * Json formats for user
  */
package object user extends SprayJsonSupport with DefaultJsonProtocol {

  implicit object UuidJsonFormat extends RootJsonFormat[UUID] {
    def write(obj: UUID) = JsString(obj.toString) //Never execute this line
    def read(value: JsValue) = value match {
      case JsString(obj) => UUID.fromString(obj)
      case JsNull        => UUID.randomUUID()
      case obj           => deserializationError("Expected UUID as JsString but got " + obj)
    }
  }

  implicit object TimestampJsonFormat extends RootJsonFormat[Timestamp] {
    def write(obj: Timestamp) = JsNumber(obj.getTime)

    def read(json: JsValue) = json match {
      case JsNumber(time) => new Timestamp(time.toLong)
      case JsNull => {
        new Timestamp((new java.util.Date()).getTime())
      }
      case _ => throw new DeserializationException("Date Expected but got " + json)
    }
  }

  implicit val userFormat = jsonFormat9(UsersRow)
}
