package com.rasterfoundry.common.datamodel

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.vector._
import geotrellis.vector.io._
import _root_.io.circe._
import _root_.io.circe.syntax._
import _root_.io.circe.parser._
import cats._
import cats.implicits._

import scala.util.Try
import java.net.URI

package object export {

  implicit val polygonEncoder: Encoder[Polygon] =
    new Encoder[Polygon] {
      def apply(mp: Polygon): Json = {
        parse(mp.toGeoJson) match {
          case Right(js: Json) => js
          case Left(e)         => throw e
        }
      }
    }

  implicit val polygonDecoder: Decoder[Polygon] = Decoder[Json] map {
    _.spaces4.parseGeoJson[Polygon]
  }
  implicit val extentEncoder: Encoder[Extent] =
    new Encoder[Extent] {
      def apply(extent: Extent): Json =
        List(extent.xmin, extent.ymin, extent.xmax, extent.ymax).asJson
    }
  implicit val extentDecoder: Decoder[Extent] =
    Decoder[Json] emap { js =>
      js.as[List[Double]]
        .map {
          case List(xmin, ymin, xmax, ymax) =>
            Extent(xmin, ymin, xmax, ymax)
        }
        .leftMap(_ => "Extent")
    }

  implicit val crsEncoder: Encoder[CRS] =
    Encoder.encodeString.contramap[CRS] { crs =>
      crs.epsgCode
        .map { c =>
          s"epsg:$c"
        }
        .getOrElse(crs.toProj4String)
    }
  implicit val crsDecoder: Decoder[CRS] =
    Decoder.decodeString.emap { str =>
      Either
        .catchNonFatal(Try(CRS.fromName(str)) getOrElse CRS.fromString(str))
        .leftMap(_ => "CRS")
    }

  implicit val uriEncoder: Encoder[URI] =
    Encoder.encodeString.contramap[URI] { _.toString }
  implicit val uriDecoder: Decoder[URI] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(URI.create(str)).leftMap(_ => "URI")
    }

  implicit lazy val celltypeDecoder: Decoder[CellType] =
    Decoder[String].map({ CellType.fromName(_) })
  implicit lazy val celltypeEncoder: Encoder[CellType] =
    Encoder.encodeString.contramap[CellType]({ CellType.toName(_) })

}
