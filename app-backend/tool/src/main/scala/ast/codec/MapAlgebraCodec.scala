package com.azavea.rf.tool.ast.codec

import com.azavea.rf.tool.ast._

import io.circe._
import io.circe.syntax._

import java.security.InvalidParameterException

trait MapAlgebraCodec
    extends MapAlgebraOperationCodecs
      with MapAlgebraLeafCodecs
      with MapAlgebraUtilityCodecs {

  implicit lazy val decodeToolRef: Decoder[MapAlgebraAST.ToolReference] =
    Decoder.forProduct2("id", "toolId")(MapAlgebraAST.ToolReference.apply)
  implicit lazy val encodeToolRef: Encoder[MapAlgebraAST.ToolReference] =
    Encoder.forProduct2("id", "toolId")(op => (op.id, op.toolId))

  /** TODO: Add codec paths besides `raster source` and `operation` when supported */
  implicit def mapAlgebraDecoder = Decoder.instance[MapAlgebraAST] { ma =>
    ma._symbol match {
      case Some(_) =>
        ma.as[MapAlgebraAST.Operation]
      case None if (ma._fields.contains("toolId")) =>
        ma.as[MapAlgebraAST.ToolReference]
      case None =>
        ma.as[MapAlgebraAST.MapAlgebraLeaf]
    }
  }

  implicit def mapAlgebraEncoder: Encoder[MapAlgebraAST] = new Encoder[MapAlgebraAST] {
    final def apply(ast: MapAlgebraAST): Json = ast match {
      case operation: MapAlgebraAST.Operation =>
        operation.asJson
      case leaf: MapAlgebraAST.MapAlgebraLeaf =>
        leaf.asJson
      case reference: MapAlgebraAST.ToolReference =>
        reference.asJson
      case _ =>
        throw new InvalidParameterException(s"Unrecognized AST: $ast")
    }
  }
}

object MapAlgebraCodec extends MapAlgebraCodec
