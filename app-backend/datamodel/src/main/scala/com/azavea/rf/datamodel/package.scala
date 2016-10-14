package com.azavea.rf

import spray.json._
import DefaultJsonProtocol._
import java.util.UUID
import java.sql.Timestamp
import java.time.Instant

import geotrellis.vector._
import geotrellis.vector.io._
import geotrellis.proj4._
import geotrellis.slick.Projected

package object datamodel {
  // Implicits necessary for full serialization
  implicit object UUIDJsonFormat extends RootJsonFormat[UUID] {
    def write(uuid: UUID): JsValue = JsString(uuid.toString)
    def read(js: JsValue): UUID = js match {
      case JsString(uuid) => UUID.fromString(uuid)
      case _ =>
        deserializationError("Failed to parse UUID string ${js} to java UUID")
    }
  }

  implicit object TimeStampJsonFormat extends RootJsonFormat[Timestamp] {
    def write(time: Timestamp) = JsString(time.toInstant().toString())

    def read(json: JsValue) = json match {
      case JsString(time) => Timestamp.from(Instant.parse(time))
      case _ => throw new DeserializationException(s"Expected ISO 8601 Date but got $json")
    }
  }

  implicit object RFExtentJsonFormat extends RootJsonFormat[Extent] {
    def write(extent: Extent): JsValue =
      JsArray(
        JsNumber(extent.xmin),
        JsNumber(extent.ymin),
        JsNumber(extent.xmax),
        JsNumber(extent.ymax)
      )
    def read(value: JsValue): Extent = value match {
      case JsArray(extent) =>
        assert(extent.size == 4)
        val parsedExtent = extent.map({
          case JsNumber(minmax) => minmax.toDouble
          case _ => deserializationError("Failed to parse extent array")
        })
        Extent(parsedExtent(0), parsedExtent(1), parsedExtent(2), parsedExtent(3))
      case _ =>
        deserializationError("Failed to parse extent array")
    }
  }

  implicit object AnyJsonFormat extends JsonFormat[Any] {
    def write(x: Any) = x match {
      case n: Int => JsNumber(n)
      case s: String => JsString(s)
      case x: Seq[_] => seqFormat[Any].write(x)
      case m: Map[String, _] => mapFormat[String, Any].write(m)
      case b: Boolean if b == true => JsTrue
      case b: Boolean if b == false => JsFalse
      case x => serializationError("Do not understand object of type " + x.getClass.getName)
    }
    def read(value: JsValue) = value match {
      case JsNumber(n) => n.intValue()
      case JsString(s) => s
      case a: JsArray => listFormat[Any].read(value)
      case o: JsObject => mapFormat[String, Any].read(value)
      case JsTrue => true
      case JsFalse => false
      case x => deserializationError("Do not understand how to deserialize " + x)
    }
  }
  // This format will/should only be used for route Geometry serialization
  implicit object ProjectedGeometryFormat extends RootJsonFormat[Projected[Geometry]] {
    // on write we convert the database geometry to LatLng
    def write(multipolygon: Projected[Geometry]): JsValue = {
      multipolygon match {
        case Projected(geom, 4326) => geom
        case Projected(geom, 3857) => geom.reproject(WebMercator, LatLng)
        case Projected(geom, srid) => try {
          geom.reproject(CRS.fromString(s"EPSG:$srid"), LatLng)
        } catch {
          case e: Exception =>
            throw new SerializationException(s"Unsupported Geometry SRID: $srid").initCause(e)
        }
      }
    }.toJson
    // on read we assume all geometries are in LatLng
    def read(value: JsValue) =
      Projected(value.convertTo[Geometry], 4326)
  }
}
