package com.azavea.rf.tool.ast.codec

import com.azavea.rf.tool.ast._

import io.circe._
import io.circe.syntax._

import java.security.InvalidParameterException

trait MapAlgebraCodec
    extends MapAlgebraSourceCodecs
       with MapAlgebraOperationCodecs
       with MapAlgebraUtilityCodecs {

  /** TODO: Add codec paths besides `raster source` and `operation` when supported */
  implicit def mapAlgebraDecoder = Decoder.instance[MapAlgebraAST] { ma =>
    (ma._type, ma._symbol) match {
      case (Some(_), None) =>
        ma.as[MapAlgebraAST.Source[_]]
      case (None, Some(_)) =>
        ma.as[MapAlgebraAST.Operation]
      case (Some(_), Some(_)) =>
        throw new InvalidParameterException(s"The JSON, '$ma.value.noSpaces', has both a 'type' and an 'apply' symbol")
      case (None, None) =>
        throw new InvalidParameterException(s"The JSON, '$ma.value.noSpaces', is not a recognized map algebra data type")
    }
  }

  implicit def mapAlgebraEncoder: Encoder[MapAlgebraAST] = new Encoder[MapAlgebraAST] {
    final def apply(ast: MapAlgebraAST): Json = ast match {
      case operation: MapAlgebraAST.Operation =>
        operation.asJson
      case source: MapAlgebraAST.Source[_] =>
        implicitly[Encoder[MapAlgebraAST.Source[_]]].apply(source)
      case _ =>
        throw new InvalidParameterException(s"Unrecognized AST: $ast")
    }
  }
}

object MapAlgebraCodec extends MapAlgebraCodec

