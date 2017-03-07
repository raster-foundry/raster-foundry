package com.azavea.rf.tool.ast.codec

import com.azavea.rf.tool.ast._

import geotrellis.raster.render._
import io.circe._
import io.circe.optics.JsonPath._
import io.circe.syntax._
import io.circe.generic.auto._

import scala.util.Try
import java.security.InvalidParameterException


trait MapAlgebraCodec
    extends MapAlgebraSourceCodecs
       with MapAlgebraOperationCodecs
       with MapAlgebraUtilityCodecs {

  /** TODO: Add codec paths besides `raster source` and `operation` when supported */
  implicit def mapAlgebraDecoder = Decoder.instance[MapAlgebraAST] { ma =>
    ma._type match {
      case Some("raster") =>
        ma.as[MapAlgebraAST.RFMLRasterSource]
      case Some(unrecognized) =>
        throw new InvalidParameterException(s"'$unrecognized' is not a recognized map algebra data type")
      case None =>
        ma.as[MapAlgebraAST.Operation]
    }
  }

  implicit def mapAlgebraEncoder: Encoder[MapAlgebraAST] = new Encoder[MapAlgebraAST] {
      final def apply(ast: MapAlgebraAST): Json = ast match {
        case operation: MapAlgebraAST.Operation => operation.asJson
        case rasterSrc: MapAlgebraAST.RFMLRasterSource => rasterSrc.asJson
        case _ => throw new InvalidParameterException(s"whoa. What even is this: $ast")
      }
    }
}
