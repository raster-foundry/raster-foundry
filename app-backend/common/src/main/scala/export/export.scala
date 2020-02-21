package com.rasterfoundry.common

import com.rasterfoundry.JsonCodecs

import geotrellis.raster.CellType
import geotrellis.vector.Polygon
import io.circe.parser.parse
import io.circe.{Decoder, Encoder, Json}

package object export extends JsonCodecs {

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

  implicit lazy val celltypeDecoder: Decoder[CellType] =
    Decoder[String].map({ CellType.fromName(_) })
  implicit lazy val celltypeEncoder: Encoder[CellType] =
    Encoder.encodeString.contramap[CellType]({ CellType.toName(_) })

}
