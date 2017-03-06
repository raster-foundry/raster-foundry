package com.azavea.rf.tool.ast.codec

import com.azavea.rf.tool.ast._

import geotrellis.raster.render._
import io.circe._
import io.circe.optics.JsonPath._
import io.circe.syntax._
import io.circe.generic.auto._

import scala.util.Try
import java.security.InvalidParameterException

object MapAlgebraCodec
    extends MapAlgebraOperationCodecs {

  implicit def mapAlgebraDecoder = Decoder.instance[MapAlgebraAST] { ma =>
    ma._type match {
      case Some("raster") =>
        ma.as[MapAlgebraAST.RFMLRasterSource]
      case Some(unrecognized) =>
        throw new InvalidParameterException(s"'$unrecognized' is not a recognized map algebra data type")
      case None =>
        ma.as[MapAlgebraAST.Operation]
      // TODO: Add these codecs when supported
      //case Some("vector") =>
      //  ma.as[MapAlgebraAST.VectorSource]
      //case Some("double") =>
      //  ma.as[MapAlgebraAST.DecimalSource]
      //case Some("int") =>
      //  ma.as[MapAlgebraAST.IntegralSource]
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
