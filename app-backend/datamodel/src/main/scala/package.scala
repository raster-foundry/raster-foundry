package com.rasterfoundry

import cats.syntax.either._
import geotrellis.proj4.{io => _, _}
import geotrellis.raster.GridExtent
import geotrellis.raster.render.{RGB, RGBA}
import geotrellis.vector.io.json.{Implicits => GeoJsonImplicits}
import geotrellis.vector.{io => _, _}
import io.circe._
import io.circe.optics.JsonPath._
import io.circe.parser._
import io.circe.syntax._

import scala.util._

import java.net.{URI, URLDecoder}
import java.security.InvalidParameterException
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SuppressWarnings(Array("CatchException"))
trait JsonCodecs extends GeoJsonImplicits {

  implicit val gridExtentEncoder: Encoder[GridExtent[Long]] =
    (a: GridExtent[Long]) =>
      Json.obj(
        ("extent", a.extent.asJson),
        ("cellWidth", a.cellwidth.asJson),
        ("cellHeight", a.cellheight.asJson),
        ("cols", a.cols.asJson),
        ("rows", a.rows.asJson)
    )

  implicit val gridExtentDecoder: Decoder[GridExtent[Long]] = (c: HCursor) =>
    for {
      extent <- c.downField("extent").as[Extent]
      cellWidth <- c.downField("cellWidth").as[Double]
      cellHeight <- c.downField("cellHeight").as[Double]
      cols <- c.downField("cols").as[Long]
      rows <- c.downField("rows").as[Long]
    } yield {
      new GridExtent[Long](extent, cellWidth, cellHeight, cols, rows)
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

  // Double key serialization
  implicit val decodeKeyDouble: KeyDecoder[Double] = new KeyDecoder[Double] {
    def apply(key: String): Option[Double] = Try(key.toDouble).toOption
  }
  implicit val encodeKeyDouble: KeyEncoder[Double] = new KeyEncoder[Double] {
    def apply(key: Double): String = key.toString
  }

  // RGBA deserialization
  implicit val decodeHexRGBA: Decoder[RGBA] = Decoder.decodeString.emap { str =>
    str.stripPrefix("#").stripPrefix("0x") match {
      case hex if (hex.size == 8) =>
        val bytes = hex
          .sliding(2, 2)
          .map({ hexByte =>
            Integer.parseInt(hexByte, 16)
          })
          .toList
        Right(RGBA(bytes(0), bytes(1), bytes(2), bytes(3)))
      case hex if (hex.size == 6) =>
        val bytes = hex
          .sliding(2, 2)
          .map({ hexByte =>
            Integer.parseInt(hexByte, 16)
          })
          .toList
        Right(RGB(bytes(0), bytes(1), bytes(2)))
      case hex => Left(s"Unable to parse $hex as an RGBA")
    }
  }
  implicit val encodeRgbaAsHex: Encoder[RGBA] =
    Encoder.encodeString.contramap[RGBA] { rgba =>
      "#" + rgba.red.toHexString + rgba.blue.toHexString + rgba.green.toHexString + rgba.alpha.toHexString
    }

  implicit val timestampEncoder: Encoder[Timestamp] =
    Encoder.encodeString.contramap[Timestamp](_.toInstant.toString)
  implicit val timestampDecoder: Decoder[Timestamp] =
    Decoder.decodeString.emap { str =>
      val timeStr: String = str.contains("+") match {
        case true  => str.dropRight(6) ++ "Z"
        case false => str
      }
      Either
        .catchNonFatal(Timestamp.from(Instant.parse(timeStr)))
        .leftMap(_ => "Timestamp")
    }

  implicit val timeRangeEncoder: Encoder[(LocalDate, LocalDate)] =
    Encoder.encodeString.contramap[(LocalDate, LocalDate)]({
      case (t1, t2) =>
        s"[$t1 00:00,$t2 00:00)"
    })
  implicit val timeRangeDecoder: Decoder[(LocalDate, LocalDate)] =
    new Decoder[(LocalDate, LocalDate)] {
      def apply(c: HCursor): Decoder.Result[(LocalDate, LocalDate)] = {
        val intervalString = c.focus map { _.noSpaces } getOrElse { "\"[,)\"" }
        val (s1, s2) = intervalString
          .replace(" 00:00", "")
          .replace("[", "")
          .replace(")", "")
          .split(",")
          .toList match {
          case h :: t :: Nil =>
            (h, t)
          case _ =>
            ("", "")
        }
        Either
          .catchNonFatal((LocalDate.parse(s1), LocalDate.parse(s2)))
          .leftMap(_ =>
            DecodingFailure(s"Could not parse local dates from ($s1, $s2)",
                            List.empty))
      }
    }

  implicit val uuidEncoder: Encoder[UUID] =
    Encoder.encodeString.contramap[UUID](_.toString)
  val withUUIDFieldUUIDDecoder: Decoder[UUID] = Decoder[JsonObject] map { js =>
    {
      val path = root.id.string
      path.getOption(js.asJson) match {
        case Some(id) => UUID.fromString(id)
        case None =>
          throw DecodingFailure(
            "no id field found in a related object",
            List.empty
          )
      }
    }
  }
  val directUUIDDecoder: Decoder[UUID] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(UUID.fromString(str)).leftMap(_ => "UUID")
    }
  implicit val uuidDecoder
    : Decoder[UUID] = directUUIDDecoder or withUUIDFieldUUIDDecoder

  implicit val uriEncoder: Encoder[URI] =
    Encoder.encodeString.contramap[URI] { _.toString }
  implicit val uriDecoder: Decoder[URI] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(URI.create(str)).leftMap(_ => "URI")
    }

  implicit val projectedGeometryEncoder: Encoder[Projected[Geometry]] =
    new Encoder[Projected[Geometry]] {
      def apply(g: Projected[Geometry]): Json = {
        val reprojected = g match {
          case Projected(geom, 4326) => geom
          case Projected(geom, 3857) => geom.reproject(WebMercator, LatLng)
          case Projected(geom, srid) =>
            try {
              geom.reproject(CRS.fromString(s"EPSG:$srid"), LatLng)
            } catch {
              case e: Exception =>
                throw new InvalidParameterException(
                  s"Unsupported Geometry SRID: $srid"
                ).initCause(e)
            }
        }
        parse(reprojected.toGeoJson) match {
          case Right(js: Json) => js
          case Left(e)         => throw e
        }
      }
    }

  implicit val projectedMultiPolygonEncoder: Encoder[Projected[MultiPolygon]] =
    new Encoder[Projected[MultiPolygon]] {
      def apply(g: Projected[MultiPolygon]): Json = {
        val reprojected = g match {
          case Projected(geom, 4326) => geom
          case Projected(geom, 3857) => geom.reproject(WebMercator, LatLng)
          case Projected(geom, srid) =>
            try {
              geom.reproject(CRS.fromString(s"EPSG:$srid"), LatLng)
            } catch {
              case e: Exception =>
                throw new InvalidParameterException(
                  s"Unsupported MultiPolygon SRID: $srid"
                ).initCause(e)
            }
        }
        parse(reprojected.toGeoJson) match {
          case Right(js: Json) => js
          case Left(e)         => throw e
        }
      }
    }

  // TODO: make this tolerate more than one incoming srid
  implicit val projectedGeometryDecoder
    : Decoder[Projected[Geometry]] = Decoder[Json] map { js =>
    Projected(js.spaces4.parseGeoJson[Geometry], 4326)
      .reproject(CRS.fromEpsgCode(4326), CRS.fromEpsgCode(3857))(3857)
  }

  implicit val projectedMultiPolygonDecoder
    : Decoder[Projected[MultiPolygon]] = Decoder[Json] map { js =>
    Projected(js.spaces4.parseGeoJson[MultiPolygon], 4326)
      .reproject(CRS.fromEpsgCode(4326), CRS.fromEpsgCode(3857))(3857)
  }
}

package object datamodel extends JsonCodecs {

  def applyWithNonEmptyString[T](s: String)(f: String => T): T =
    s.filter(_ != '\u0000').mkString match {
      case "" =>
        throw new IllegalArgumentException(
          "Cannot instantiate with empty string"
        )
      case `s` => f(s)
    }

  def applyWithNonEmptyString[T](
      s: Option[String]
  )(f: Option[String] => T): T = {
    s map { _.filter(_ != '\u0000') } match {
      case Some("") =>
        throw new IllegalArgumentException(
          "Cannot instantiate with empty string or string with only null bytes"
        )
      case _ => f(s)
    }
  }

  trait OwnerCheck {
    def checkOwner(createUser: User, ownerUserId: Option[String]): String = {
      (createUser, ownerUserId) match {
        case (user, Some(id)) if user.id == id    => user.id
        case (user, Some(id)) if user.isSuperuser => id
        case (user, Some(_)) if !user.isSuperuser =>
          throw new IllegalArgumentException(
            "Insufficient permissions to set owner on object"
          )
        case (user, _) => user.id
      }
    }
  }

  // Lifted from ProjectDao removeLayerOverview method --
  // it's not clear what sort of common place this URI parsing logic should live in
  // so it's duplicated here
  def uriToBucketAndKey(s: String): (String, String) = {
    val uri = URI.create(s)
    val urlPath = uri.getPath()
    val bucket = URLDecoder.decode(uri.getHost(), "UTF-8")
    val key = URLDecoder.decode(urlPath.drop(1), "UTF-8")
    (bucket, key)
  }
}
