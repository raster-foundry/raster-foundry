package com.azavea.rf.ingest

import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.proj4._
import geotrellis.vector._
import geotrellis.spark._
import geotrellis.spark.io.index._
import spray.json._
import DefaultJsonProtocol._

import java.util.UUID
import java.net.URI

package object model {

  implicit class HasCellSize[A <: { def rows: Int; def cols: Int; def extent: Extent }](obj: A) {
    def cellSize: CellSize = CellSize(obj.extent.width / obj.cols, obj.extent.height / obj.rows)
  }

  implicit object KeyIndexMethodJsonFormat extends RootJsonFormat[KeyIndexMethod[SpatialKey]] {
    def write(kim: KeyIndexMethod[SpatialKey]): JsValue =
      JsString("Unable to serialize KeyIndexMethods")

    def read(value: JsValue): KeyIndexMethod[SpatialKey] = value match {
      case JsString(kim) if kim.toLowerCase == "zcurvekeyindexmethod" => ZCurveKeyIndexMethod
      case JsString(kim) if kim.toLowerCase == "rowmajorkeyindexmethod" => RowMajorKeyIndexMethod
      case JsString(kim) if kim.toLowerCase == "hilbertkeyindexmethod" => HilbertKeyIndexMethod
      case _ =>
        deserializationError("Failed to parse Key Index Method")
    }
  }

  implicit object ResampleMethodJsonFormat extends RootJsonFormat[ResampleMethod] {
    def write(rm: ResampleMethod): JsValue = rm match {
      case NearestNeighbor => JsString("NearestNeighbor")
      case Bilinear => JsString("Bilinear")
      case CubicConvolution => JsString("CubicConvolution")
      case CubicSpline => JsString("CubicSpline")
      case Lanczos => JsString("Lanczos")
      case Average => JsString("Average")
      case Mode => JsString("Mode")
      case Median => JsString("Median")
      case Max => JsString("Max")
      case Min => JsString("Min")
    }

    def read(value: JsValue): ResampleMethod = value match {
      case JsString(rm) if rm.toLowerCase == "nearestneighbor" => NearestNeighbor
      case JsString(rm) if rm.toLowerCase == "bilinear" => Bilinear
      case JsString(rm) if rm.toLowerCase == "cubicconvolution" => CubicConvolution
      case JsString(rm) if rm.toLowerCase == "cubicspline" => CubicSpline
      case JsString(rm) if rm.toLowerCase == "lanczos" => Lanczos
      case JsString(rm) if rm.toLowerCase == "average" => Average
      case JsString(rm) if rm.toLowerCase == "mode" => Mode
      case JsString(rm) if rm.toLowerCase == "median" => Median
      case JsString(rm) if rm.toLowerCase == "max" => Max
      case JsString(rm) if rm.toLowerCase == "min" => Min
      case _ =>
        deserializationError("Failed to parse resample method")
    }
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

  implicit object ProjectedExtentJsonFormat extends RootJsonFormat[ProjectedExtent] {
    def write(pe: ProjectedExtent): JsValue = pe.crs.epsgCode match {
      case Some(epsg) =>
        JsObject(
          "bbox" -> pe.extent.toJson,
          "crs" -> pe.crs.toJson
        )
      case None =>
        serializationError(s"Unknown epsg code for ${pe.crs}")
    }

    def read(value: JsValue): ProjectedExtent = value.asJsObject.getFields("bbox", "crs") match {
      case Seq(extent, crs) =>
        ProjectedExtent(extent.convertTo[Extent], crs.convertTo[CRS])
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
        deserializationError(s"Failed to parse ${value} to cellsize")
    }
  }

  implicit object cellTypeJsonFormat extends JsonFormat[CellType] {
    def write(ct: CellType) = JsString(ct.toString)

    def read(value: JsValue) = value match {
      case JsString(ct) =>
        CellType.fromString(ct)
      case _ =>
        deserializationError(s"Failed to parse ${value} to CellType")
    }
  }
}
