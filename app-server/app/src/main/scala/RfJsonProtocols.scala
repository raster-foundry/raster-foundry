package com.azavea.rf

import com.azavea.rf.datamodel._
import com.azavea.rf.database.RFDatabaseJsonProtocol

import java.util.UUID
import java.sql.Timestamp
import java.time.Instant
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

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

  /** Serialization for ThumbnailSize enum */
  implicit object ThumbnailSizeFormat extends RootJsonFormat[ThumbnailSize] {
    def write(thumbnailSize: ThumbnailSize) = JsString(thumbnailSize.toString)
    def read(value: JsValue) = value match {
      case JsString(enum) => ThumbnailSize.fromString(enum)
      case _ => throw new DeserializationException(s"Expected thumbnail size value but got $value")
    }
  }

  implicit val userFormat = jsonFormat1(User)
  implicit val organizationFormat = jsonFormat4(Organization)
  implicit val imagesRowFormat = jsonFormat13(Image)
  implicit val thumbnailsRowFormat = jsonFormat9(Thumbnail)
}
