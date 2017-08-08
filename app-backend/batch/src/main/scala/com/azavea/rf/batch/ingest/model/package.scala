package com.azavea.rf.batch.ingest

import io.circe._
import io.circe.syntax._

import com.azavea.rf.bridge._

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.spark._
import geotrellis.spark.io.index._
import geotrellis.vector._
import cats.syntax.either._

import spray.json._

package object model {
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

  implicit val keyIndexEncoder: Encoder[KeyIndexMethod[SpatialKey]] =
    Encoder.encodeString.contramap[KeyIndexMethod[SpatialKey]](_ => "Unable to serialize KeyIndexMethods")

  implicit val keyIndexDecoder: Decoder[KeyIndexMethod[SpatialKey]] =
    Decoder.decodeString.emap { str =>
      val keyIndexMethod: KeyIndexMethod[SpatialKey] = str match {
        case kim if kim.toLowerCase == "zcurvekeyindexmethod" => ZCurveKeyIndexMethod
        case kim if kim.toLowerCase == "rowmajorkeyindexmethod" => RowMajorKeyIndexMethod
        case kim if kim.toLowerCase == "hilbertkeyindexmethod" => HilbertKeyIndexMethod
        case kim => throw new Exception(s"Unable to deserialize KeyIndexMehods[SpatialKey]: ${kim}")
      }

      Either.catchNonFatal(keyIndexMethod).leftMap(_ => "KeyIndexMethod[SpatialKey]")
    }

  implicit val resampleMethodEncoder: Encoder[ResampleMethod] =
    Encoder.encodeString.contramap[ResampleMethod] { rm =>
      rm match {
        case NearestNeighbor => "NearestNeighbor"
        case Bilinear => "Bilinear"
        case CubicConvolution => "CubicConvolution"
        case CubicSpline => "CubicSpline"
        case Lanczos => "Lanczos"
        case Average => "Average"
        case Mode => "Mode"
        case Median => "Median"
        case Max => "Max"
        case Min => "Min"
      }
    }

  implicit val resampleMethodDecoder: Decoder[ResampleMethod] =
    Decoder.decodeString.emap { str =>
      val resampleMethod: ResampleMethod = str match {
        case rm if rm.toLowerCase == "nearestneighbor" => NearestNeighbor
        case rm if rm.toLowerCase == "bilinear" => Bilinear
        case rm if rm.toLowerCase == "cubicconvolution" => CubicConvolution
        case rm if rm.toLowerCase == "cubicspline" => CubicSpline
        case rm if rm.toLowerCase == "lanczos" => Lanczos
        case rm if rm.toLowerCase == "average" => Average
        case rm if rm.toLowerCase == "mode" => Mode
        case rm if rm.toLowerCase == "median" => Median
        case rm if rm.toLowerCase == "max" => Max
        case rm if rm.toLowerCase == "min" => Min
        case rm => throw new Exception(s"Unable to deserialize ResampleMethod: ${rm}")
      }

      Either.catchNonFatal(resampleMethod).leftMap(_ => "ResampleMethod")
    }

  implicit val projectedExtentEncoder: Encoder[ProjectedExtent] =
    Encoder.encodeString.contramap[ProjectedExtent] { pe =>
      pe.crs.epsgCode match {
        case Some(epsg) =>
          JsonObject.fromMap(
            Map(
              "bbox" -> pe.extent.asJson,
              "crs" -> pe.crs.asJson
            )
          ).asJson.noSpaces
        case None => throw new Exception(s"Unknown epsg code for ${pe.crs}")
      }
    }

  implicit val projectedExtentDecoder: Decoder[ProjectedExtent] =
    Decoder[Json] emap { js =>
      js.as[JsonObject].map { jso =>
        val map = jso.toMap
        (map.get("bbox"), map.get("crs")) match {
          case (Some(extent), Some(crs)) => {
            (extent.as[Extent].toOption, crs.as[CRS].toOption) match {
              case (Some(extent), Some(crs)) => ProjectedExtent(extent, crs)
              case value => throw new Exception(s"Can't decode ProjectedExtent: $value")
            }
          }
          case value => throw new Exception(s"Can't decode ProjectedExtent: $value")
        }
      } leftMap (_ => "ProjectedExtent")
    }

  implicit val cellSizeEncoder: Encoder[CellSize] =
    Encoder.encodeString.contramap[CellSize] { sz =>
      JsonObject.fromMap(
        Map(
          "width" -> sz.width.asJson,
          "height" -> sz.height.asJson
        )
      ).asJson.noSpaces
    }

  implicit val cellSizeDecoder: Decoder[CellSize] =
    Decoder[Json] emap { js =>
      js.as[JsonObject].map { jso =>
        val map = jso.toMap
        (map.get("width"), map.get("height")) match {
          case (Some(width), Some(height)) => {
            (width.as[Double].toOption, height.as[Double].toOption) match {
              case (Some(width), Some(height)) => CellSize(width, height)
              case value => throw new Exception(s"Can't decode CellSize: $value")
            }
          }
          case value => throw new Exception(s"Can't decode CellSize: $value")
        }
      } leftMap (_ => "CellSize")
    }


  implicit val cellTypeEncoder: Encoder[CellType] =
    Encoder.encodeString.contramap[CellType] { _.toString }
  implicit val cellTypeDecoder: Decoder[CellType] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(CellType.fromName(str)).leftMap(_ => "CellType")
    }
}
