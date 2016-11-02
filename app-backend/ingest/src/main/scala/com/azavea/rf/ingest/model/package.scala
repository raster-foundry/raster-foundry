package com.azavea.rf.ingest

import geotrellis.raster._
import geotrellis.proj4._
import spray.json._
import DefaultJsonProtocol._

import java.util.UUID
import java.net.URI

import geotrellis.vector.Extent

package object model {
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
          case JsNumber(minmax) =>
            minmax.toDouble
          case _ =>
            deserializationError("Failed to parse extent array")
        })
        Extent(parsedExtent(0), parsedExtent(1), parsedExtent(2), parsedExtent(3))
      case _ =>
        deserializationError("Failed to parse extent array")
    }
  }

	implicit object UUIDJsonFormat extends RootJsonFormat[UUID] {
		def write(uuid: UUID): JsValue =
			JsString(uuid.toString)

		def read(value: JsValue): UUID = value match {
			case JsString(uuid) =>
				UUID.fromString(uuid)
			case _ =>
        deserializationError(s"Expected valid java UUID, got: $value")
		}
	}

	implicit object URIJsonFormat extends RootJsonFormat[URI] {
		def write(uri: URI): JsValue = JsString(uri.toString)
		def read(value: JsValue): URI = value match {
			case JsString(uri) =>
				URI.create(uri)
			case _ =>
        deserializationError(s"Expected java URI, got: $value")
    }
  }

  implicit object CellSizeJsonFormat extends RootJsonFormat[CellSize] {
    def write(cs: CellSize) = JsObject(
      "width" -> JsNumber(cs.width),
      "height" -> JsNumber(cs.height)
    )

    def read(value: JsValue) =
      value.asJsObject.getFields("width", "height") match {
      case Seq(JsNumber(width), JsNumber(height)) =>
        CellSize(width.toDouble, height.toDouble)
      case _ =>
        deserializationError("Failed to parse ${value} to cellsize")
    }
  }

  implicit object cellTypeJsonFormat extends JsonFormat[CellType] {
    def write(ct: CellType) = JsString(ct.toString)

    def read(value: JsValue) = value match {
      case JsString(ct) =>
        CellType.fromString(ct)
      case _ =>
        deserializationError("Failed to parse ${value} to CellType")
    }
  }

  implicit object crsJsonFormat extends JsonFormat[CRS] {
    def write(crs: CRS) =
      crs.epsgCode match {
        case Some(epsg) =>
          JsString(s"epsg:${epsg}")
        case None =>
          serializationError("Unknown epsg code for ${crs}")
      }

    def read(value: JsValue): CRS = value match {
      case JsString(epsg) =>
        CRS.fromName(epsg)
      case _ =>
        deserializationError("Failed to parse ${value} to CRS")
    }
  }
}
