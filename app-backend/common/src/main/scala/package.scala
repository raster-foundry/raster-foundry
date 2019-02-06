package com.rasterfoundry

import scala.util.Try

import cats.syntax.either._
import geotrellis.proj4.CRS
import geotrellis.vector.{Extent, MultiPolygon}
import geotrellis.vector.io._
import io.circe._
import io.circe.parser._
import io.circe.syntax._

package object common {

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

  implicit val multipolygonEncoder: Encoder[MultiPolygon] =
    new Encoder[MultiPolygon] {
      def apply(mp: MultiPolygon): Json = {
        parse(mp.toGeoJson) match {
          case Right(js: Json) => js
          case Left(e)         => throw e
        }
      }
    }

  implicit val multipolygonDecoder: Decoder[MultiPolygon] = Decoder[Json] map {
    _.spaces4.parseGeoJson[MultiPolygon]
  }
}
