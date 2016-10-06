package com.azavea.rf.ingest

import spray.json._

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
          case JsNumber(minmax) => minmax.toDouble
          case _ => deserializationError("Failed to parse extent array")
        })
        Extent(parsedExtent(0), parsedExtent(1), parsedExtent(2), parsedExtent(3))
      case _ =>
        deserializationError("Failed to parse extent array")
    }
  }
}
