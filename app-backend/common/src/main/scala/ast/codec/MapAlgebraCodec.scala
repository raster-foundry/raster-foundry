package com.rasterfoundry.common.ast.codec

import com.rasterfoundry.common.ast._

import io.circe._
import io.circe.syntax._

import java.security.InvalidParameterException

trait MapAlgebraCodec
    extends MapAlgebraOperationCodecs
    with MapAlgebraLeafCodecs
    with MapAlgebraUtilityCodecs {

  /** TODO: Add codec paths besides `raster source` and `operation` when supported */
  implicit def mapAlgebraDecoder = Decoder.instance[MapAlgebraAST] { ma =>
    ma.symbolOpt match {
      case Some(_) =>
        ma.as[MapAlgebraAST.Operation]
      case None =>
        ma.as[MapAlgebraAST.MapAlgebraLeaf]
    }
  }

  implicit def mapAlgebraEncoder: Encoder[MapAlgebraAST] =
    new Encoder[MapAlgebraAST] {
      def apply(ast: MapAlgebraAST): Json = ast match {
        case operation: MapAlgebraAST.Operation =>
          operation.asJson
        case leaf: MapAlgebraAST.MapAlgebraLeaf =>
          leaf.asJson
        case _ =>
          throw new InvalidParameterException(s"Unrecognized AST: $ast")
      }
    }
}

object MapAlgebraCodec extends MapAlgebraCodec
