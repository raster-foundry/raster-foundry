package com.azavea.rf.export

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.vector._

import spray.json._

import java.net.URI
import java.util.UUID

package object model {

  implicit class HasCellSize[A <: { def rows: Int; def cols: Int; def extent: Extent }](obj: A) {
    def cellSize: CellSize = CellSize(obj.extent.width / obj.cols, obj.extent.height / obj.rows)
  }

  implicit object CRSJsonFormat extends JsonFormat[CRS] {
    def write(crs: CRS) =
      crs.epsgCode match {
        case Some(epsg) =>
          JsString(s"epsg:${epsg}")
        case None =>
          serializationError(s"Unknown epsg code for ${crs}")
      }

    def read(value: JsValue): CRS = value match {
      case JsString(epsg) =>
        CRS.fromName(epsg)
      case _ =>
        deserializationError(s"Failed to parse ${value} to CRS")
    }
  }

  implicit object ExtentJsonFormat extends RootJsonFormat[Extent] {
    def write(extent: Extent): JsValue = JsArray(
      JsNumber(extent.xmin),
      JsNumber(extent.ymin),
      JsNumber(extent.xmax),
      JsNumber(extent.ymax)
    )

    def read(value: JsValue): Extent = value match {
      case JsArray(extent) if extent.size == 4 =>
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
}
