package com.azavea.rf.tool.ast.codec

import com.azavea.rf.tool.ast._

import geotrellis.raster.render._
import io.circe._
import io.circe.syntax._

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

  implicit val colorRampDecoder: Decoder[ColorRamp] =
    Decoder[Vector[Int]].map({ ColorRamp(_) })

  implicit val colorRampEncoder: Encoder[ColorRamp] = new Encoder[ColorRamp] {
    final def apply(cRamp: ColorRamp): Json = cRamp.colors.toArray.asJson
  }
}

