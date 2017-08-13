package com.azavea.rf.tool.ast.codec

import com.azavea.rf.tool.ast._

import geotrellis.raster.io._
import geotrellis.raster.histogram._
import geotrellis.raster.summary.Statistics
import geotrellis.raster.render._
import geotrellis.raster.mapalgebra.focal._
import cats.syntax.either._
import spray.json._
import DefaultJsonProtocol._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.generic.semiauto._
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
      case unrecognized =>
        throw new InvalidParameterException(s"'$unrecognized' is not a recognized ClassBoundaryType")
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
      case Some("circle") => n.as[Circle]
      case Some("nesw") => n.as[Nesw]
      case Some("wedge") => n.as[Wedge]
      case Some("annulus") => n.as[Annulus]
      case unrecognized => Left(DecodingFailure(s"Unrecognized neighborhood: $unrecognized", n.history))
    }
  }

  implicit val neighborhoodEncoder: Encoder[Neighborhood] = new Encoder[Neighborhood] {
    final def apply(n: Neighborhood): Json = n match {
      case square: Square => square.asJson
      case circle: Circle => circle.asJson
      case nesw: Nesw => nesw.asJson
      case wedge: Wedge => wedge.asJson
      case annulus: Annulus => annulus.asJson
      case unrecognized =>
        throw new InvalidParameterException(s"Unrecognized neighborhood: $unrecognized")
    }
  }

  implicit val squareNeighborhoodDecoder: Decoder[Square] =
    Decoder.forProduct1("extent")(Square.apply)
  implicit val squareNeighborhoodEncoder: Encoder[Square] =
    Encoder.forProduct2("extent", "type")(op => (op.extent, "square"))

  implicit val circleNeighborhoodDecoder: Decoder[Circle] =
    Decoder.forProduct1("radius")(Circle.apply)
  implicit val circleNeighborhoodEncoder: Encoder[Circle] =
    Encoder.forProduct2("radius", "type")(op => (op.extent, "circle"))

  implicit val neswNeighborhoodDecoder: Decoder[Nesw] =
    Decoder.forProduct1("extent")(Nesw.apply)
  implicit val neswNeighborhoodEncoder: Encoder[Nesw] =
    Encoder.forProduct2("extent", "type")(op => (op.extent, "nesw"))

  implicit val wedgeNeighborhoodDecoder: Decoder[Wedge] =
    Decoder.forProduct3("radius", "startAngle", "endAngle")(Wedge.apply)
  implicit val wedgeNeighborhoodEncoder: Encoder[Wedge] =
    Encoder.forProduct4("radius", "startAngle", "endAngle", "type")(op => (op.radius, op.startAngle, op.endAngle, "wedge"))

  implicit val annulusNeighborhoodDecoder: Decoder[Annulus] =
    Decoder.forProduct2("innerRadius", "outerRadius")(Annulus.apply)
  implicit val annulusNeighborhoodEncoder: Encoder[Annulus] =
    Encoder.forProduct3("innerRadius", "outerRadius", "type")(op => (op.innerRadius, op.outerRadius, "annulus"))

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

  implicit val statsDecoder: Decoder[Statistics[Double]] = deriveDecoder
  implicit val statsEncoder: Encoder[Statistics[Double]] = deriveEncoder

  implicit val sprayJsonEncoder: Encoder[JsValue] = new Encoder[JsValue] {
    final def apply(jsvalue: JsValue): Json = parse(jsvalue.compactPrint) match {
      case Right(success) => success
      case Left(fail) => throw fail
    }
  }

  val defaultClassMapDecoder: Decoder[ClassMap] = deriveDecoder[ClassMap]
  val hexClassMapDecoder: Decoder[ClassMap] = new Decoder[ClassMap] {
    final def apply(c: HCursor): Decoder.Result[ClassMap] = {
      for {
        hexes <- c.downField("classifications").as[Map[Double, String]]
      } yield {
        new ClassMap(hexes.mapValues(new java.math.BigInteger(_, 16).intValue()))
      }
    }
  }
  implicit val classMapDecoder: Decoder[ClassMap] = defaultClassMapDecoder or hexClassMapDecoder
  implicit val classMapEncoder: Encoder[ClassMap] = deriveEncoder[ClassMap]
}

