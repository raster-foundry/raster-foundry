package com.azavea.rf

import java.util.UUID
import java.security.InvalidParameterException
import java.sql.Timestamp
import java.time.Instant

import io.circe._
import io.circe.parser._
import cats.syntax.either._

import geotrellis.vector._
import geotrellis.vector.io._
import geotrellis.vector.io.json._
import geotrellis.proj4._
import geotrellis.slick.Projected

package object datamodel {
  implicit def encodePaginated[A: Encoder] =
    Encoder.forProduct6(
      "count",
      "hasPrevious",
      "hasNext",
      "page",
      "pageSize",
      "results"
    )({
        pr: PaginatedResponse[A] =>
        (pr.count, pr.hasPrevious, pr.hasNext, pr.page, pr.pageSize, pr.results)
      }
    )

  implicit val timestampEncoder: Encoder[Timestamp] =
    Encoder.encodeString.contramap[Timestamp](_.toInstant.toString)
  implicit val timestampDecoder: Decoder[Timestamp] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(Timestamp.from(Instant.parse(str))).leftMap(_ => "Timestamp")
    }

  implicit val uuidEncoder: Encoder[UUID] =
    Encoder.encodeString.contramap[UUID](_.toString)
  implicit val uuidDecoder: Decoder[UUID] =
    Decoder.decodeString.emap { str =>
    Either.catchNonFatal(UUID.fromString(str)).leftMap(_ => "UUID")
  }

  implicit val projectedGeometryEncoder: Encoder[Projected[Geometry]] =
    new Encoder[Projected[Geometry]] {
      final def apply(g: Projected[Geometry]): Json = {
        val reprojected = g match {
          case Projected(geom, 4326) => geom
          case Projected(geom, 3857) => geom.reproject(WebMercator, LatLng)
          case Projected(geom, srid) => try {
            geom.reproject(CRS.fromString(s"EPSG:$srid"), LatLng)
          } catch {
            case e: Exception =>
              throw new InvalidParameterException(s"Unsupported Geometry SRID: $srid").initCause(e)
          }
        }
        parse(reprojected.toGeoJson) match {
          case Right(js: Json) => js
          case Left(e) => throw e
        }
      }
    }

  // TODO: make this tolerate more than one incoming srid
  implicit val projectedGeometryDecoder: Decoder[Projected[Geometry]] = Decoder[Json] map { js =>
    Projected(js.spaces4.parseGeoJson[Geometry], 4326).reproject(CRS.fromEpsgCode(4326), CRS.fromEpsgCode(3857))(3857)
  }
}
