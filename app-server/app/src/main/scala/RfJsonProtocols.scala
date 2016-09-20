package com.azavea.rf

import java.util.UUID
import java.sql.Timestamp
import java.time.Instant

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

import com.azavea.rf.datamodel.latest.schema.tables.UsersRow
import com.azavea.rf.datamodel.latest.schema.tables.OrganizationsRow
import com.azavea.rf.datamodel.enums._
import com.azavea.rf.datamodel.driver.RFDatabaseJsonProtocol

trait RfJsonProtocols extends SprayJsonSupport
    with DefaultJsonProtocol
    with RFDatabaseJsonProtocol {

  implicit object UuidJsonFormat extends RootJsonFormat[UUID] {
    def write(uuid: UUID) = JsString(uuid.toString)
    def read(value: JsValue) = value match {
      case JsString(uuid) => UUID.fromString(uuid)
      case _ => deserializationError(s"Expected UUID but got $value")
    }
  }

  /** Serialization for visibility enum */
  implicit object VisibilityFormat extends RootJsonFormat[Visibility] {

    def write(visibility: Visibility) = JsString(visibility.toString)
    def read(value: JsValue) = value match {
      case JsString(enum) => Visibility.fromString(enum)
      case _ => throw new DeserializationException(s"Expected visibility value but got $value")
    }
  }

  /** Serialization for JobStatus enum */
  implicit object JobStatusFormat extends RootJsonFormat[JobStatus] {

    def write(jobStatus: JobStatus) = JsString(jobStatus.toString)
    def read(value: JsValue) = value match {
      case JsString(enum) => JobStatus.fromString(enum)
      case _ => throw new DeserializationException(s"Expected job status value but got $value")
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
