package com.azavea.rf.tool.ast.codec

import com.azavea.rf.tool.ast._

import geotrellis.raster.io._
import geotrellis.raster.histogram._
import geotrellis.raster.render._
import geotrellis.raster.mapalgebra.focal._
import spray.json._
import DefaultJsonProtocol._
import io.circe._
import io.circe.syntax._
import io.circe.parser._

import java.security.InvalidParameterException
import java.util.UUID
import scala.util.Try

trait MapAlgebraUtilityCodecs {
  implicit def mapAlgebraDecoder: Decoder[MapAlgebraAST]
  implicit def mapAlgebraEncoder: Encoder[MapAlgebraAST]

  implicit val decodeKeyDouble: KeyDecoder[Double] = new KeyDecoder[Double] {
    final def apply(key: String): Option[Double] = Try(key.toDouble).toOption
  }
  implicit val encodeKeyDouble: KeyEncoder[Double] = new KeyEncoder[Double] {
    final def apply(key: Double): String = key.toString
  }

  implicit val decodeKeyUUID: KeyDecoder[UUID] = new KeyDecoder[UUID] {
    final def apply(key: String): Option[UUID] = Try(UUID.fromString(key)).toOption
  }
  implicit val encodeKeyUUID: KeyEncoder[UUID] = new KeyEncoder[UUID] {
    final def apply(key: UUID): String = key.toString
  }

  implicit lazy val classBoundaryDecoder: Decoder[ClassBoundaryType] =
    Decoder[String].map {
      case "lessThan" => LessThan
      case "lessThanOrEqualTo" => LessThanOrEqualTo
      case "exact" => Exact
      case "greaterThanOrEqualTo" => GreaterThanOrEqualTo
      case "greaterThan" => GreaterThan
    }

  implicit lazy val classBoundaryEncoder: Encoder[ClassBoundaryType] =
    Encoder.encodeString.contramap[ClassBoundaryType]({ cbType =>
      cbType match {
        case LessThan => "lessThan"
        case LessThanOrEqualTo => "lessThanOrEqualTo"
        case Exact => "exact"
        case GreaterThanOrEqualTo => "greaterThanOrEqualTo"
        case GreaterThan => "greaterThan"
        case unrecognized =>
          throw new InvalidParameterException(s"'$unrecognized' is not a recognized ClassBoundaryType")
      }
    })

  implicit val neighborhoodDecoder: Decoder[Neighborhood] = Decoder.instance[Neighborhood] { n =>
    n._type match {
      case Some("square") => n.as[Square]
      case unrecognized => Left(DecodingFailure(s"Unrecognized neighborhood: $unrecognized", n.history))
    }
  }

  implicit val neighborhoodEncoder: Encoder[Neighborhood] = new Encoder[Neighborhood] {
    final def apply(n: Neighborhood): Json = n match {
      case square: Square => square.asJson
      case unrecognized =>
        throw new InvalidParameterException(s"Unrecognized neighborhood: $unrecognized")
    }
  }

  implicit val squareNeighborhoodDecoder: Decoder[Square] =
    Decoder.forProduct1("extent")(Square.apply)
  implicit val squareNeighborhoodEncoder: Encoder[Square] =
    Encoder.forProduct2("extent", "type")(op => (op.extent, "square"))

  implicit val colorRampDecoder: Decoder[ColorRamp] =
    Decoder[Vector[Int]].map({ ColorRamp(_) })

  implicit val colorRampEncoder: Encoder[ColorRamp] = new Encoder[ColorRamp] {
    final def apply(cRamp: ColorRamp): Json = cRamp.colors.toArray.asJson
  }

  implicit val histogramDecoder: Decoder[Histogram[Double]] = Decoder[Json].map { js =>
    js.noSpaces.parseJson.convertTo[Histogram[Double]]
  }

  implicit val histogramEncoder: Encoder[Histogram[Double]] = new Encoder[Histogram[Double]] {
    final def apply(hist: Histogram[Double]): Json = hist.toJson.asJson
  }

  implicit val sprayJsonEncoder: Encoder[JsValue] = new Encoder[JsValue] {
    final def apply(jsvalue: JsValue): Json = parse(jsvalue.compactPrint) match {
      case Right(success) => success
      case Left(fail) => throw fail
    }
  }
}

