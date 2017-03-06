package com.azavea.rf.tool.ast

import geotrellis.raster.render._
import io.circe._
import io.circe.optics.JsonPath._

import scala.util.Try
import java.security.InvalidParameterException


case class ClassBreaks(boundaryType: ClassBoundaryType, classMap: Map[Double, Double])

object ClassBreaks {
  implicit lazy val classBreaksDecoder = Decoder.instance[ClassBreaks] { cbreaks =>
    val maybeBoundaryType =
      root.boundaryType.as[ClassBoundaryType].getOption(cbreaks.value)
    val maybeMap =
      root.classMap.as[Map[Double, Double]].getOption(cbreaks.value)
    ???
  }

  // Codec necessary for interpreting JSON keys as Double
  implicit val decodeKeyDouble: KeyDecoder[Double] = new KeyDecoder[Double] {
    final def apply(key: String): Option[Double] = Try(key.toDouble).toOption
  }
  implicit val encodeKeyDouble: KeyEncoder[Double] = new KeyEncoder[Double] {
    final def apply(key: Double): String = key.toString
  }

  implicit lazy val classBoundaryDecoder: Decoder[ClassBoundaryType] =
    Decoder[String].map {
      case "greaterThan" => GreaterThan
      case "greaterThanOrEqualTo" => GreaterThanOrEqualTo
      case "lessThan" => LessThan
      case "lessThanOrEqualTo" => LessThanOrEqualTo
      case "exact" => Exact
      case unrecognized =>
        throw new InvalidParameterException(s"'$unrecognized' is not a recognized ClassBoundaryType")
    }
  implicit lazy val classBoundaryEncoder: Encoder[ClassBoundaryType] = ???

}

