package com.azavea.rf.datamodel

import spray.json._
import spray.json.DefaultJsonProtocol._

import java.sql.Timestamp
import java.util.UUID

trait SerializationUtils {
  def jsArrayToList[T](jsArr: JsValue): List[T] = {
    jsArr match {
      case arr: JsArray => arr.elements.map(_.asInstanceOf[T]).to[List]
      case _ => List.empty[T]
    }
  }

  // Reimplements OptionFormat.read because OptionFormat is mysteriously not in scope
  def jsOptionToVal[T: JsonFormat](jsOpt: JsValue): Option[T] = {
    try {
      jsOpt match {
        case JsNull => None
        case v: JsValue => Some(v.convertTo[T])
      }
    }
  }

  def formatTs(ts: String): Timestamp = {
    Timestamp.valueOf(
      ts.replace("Z", "").replace("T", " ")
    )
  }

  def formatTs(ts: Option[String]): Option[Timestamp] = {
    ts match {
      case Some(t) => Some(formatTs(t))
      case _ => None
    }
  }

  /** Serializers for simple types not covered by DefaultJsonProtocol */
  implicit object TimestampOptionJsonProtocol extends RootJsonFormat[Option[Timestamp]] {
    def write(ts: Option[Timestamp]): JsValue = {
      ts match {
        case Some(t) => t.toJson
        case _ => JsNull
      }
    }

    def read(v: JsValue): Option[Timestamp] = {
      v match {
        case JsNull => None
        case _ => Some(formatTs(StringJsonFormat.read(v)))
      }
    }
  }

  implicit object UUIDOptionJsonProtocol extends RootJsonFormat[Option[UUID]] {
    def write(uuid: Option[UUID]): JsValue = {
      uuid match {
        case Some(u) => u.toJson
        case _ => JsNull
      }
    }

    def read(v: JsValue): Option[UUID] = {
      v match {
        case JsNull => None
        case s: JsString => Some(UUID.fromString(StringJsonFormat.read(s)))
        case _ => throw new DeserializationException("Invalid UUID parameter: $v")
      }
    }
  }

  implicit object IterableUUIDJsonProtocol extends RootJsonFormat[Iterable[UUID]] {
    def write(uuids: Iterable[UUID]): JsArray = JsArray(
      (uuids map { uuid => uuid.toJson }).to[List]
    )

    def read(uuidsArr: JsValue): Iterable[UUID] = jsArrayToList[UUID](uuidsArr)
  }
}
