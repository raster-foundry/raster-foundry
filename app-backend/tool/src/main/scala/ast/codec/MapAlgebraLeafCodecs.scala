package com.azavea.rf.tool.ast.codec

import com.azavea.rf.tool.ast._

import io.circe._
import io.circe.syntax._

import java.security.InvalidParameterException

trait MapAlgebraLeafCodecs {
  implicit def mapAlgebraDecoder: Decoder[MapAlgebraAST]
  implicit def mapAlgebraEncoder: Encoder[MapAlgebraAST]

  /** TODO: Add codec paths besides `raster source` and `operation` when supported */
  implicit def mapAlgebraLeafDecoder = Decoder.instance[MapAlgebraAST.MapAlgebraLeaf] { ma =>
    ma._type match {
      case Some("src") =>
        ma.as[MapAlgebraAST.Source]
      case Some("const") =>
        ma.as[MapAlgebraAST.Constant]
      case _ =>
        Left(DecodingFailure(s"Unrecognized leaf node: $ma", ma.history))
    }
  }

  implicit def mapAlgebraLeafEncoder: Encoder[MapAlgebraAST.MapAlgebraLeaf] = new Encoder[MapAlgebraAST.MapAlgebraLeaf] {
    final def apply(ast: MapAlgebraAST.MapAlgebraLeaf): Json = ast match {
      case src: MapAlgebraAST.Source =>
        src.asJson
      case const: MapAlgebraAST.Constant =>
        const.asJson
      case _ =>
        throw new InvalidParameterException(s"Unrecognized AST: $ast")
    }
  }

  implicit lazy val decodeSource: Decoder[MapAlgebraAST.Source] =
    Decoder.forProduct2("id", "metadata")(MapAlgebraAST.Source.apply)
  implicit lazy val encodeSource: Encoder[MapAlgebraAST.Source] =
    Encoder.forProduct3("type", "id", "metadata")(op => (op.`type`, op.id, op.metadata))

  implicit lazy val decodeConstant: Decoder[MapAlgebraAST.Constant] =
    Decoder.forProduct3("id", "constant", "metadata")(MapAlgebraAST.Constant.apply)
  implicit lazy val encodeConstant: Encoder[MapAlgebraAST.Constant] =
    Encoder.forProduct4("type", "id", "constant", "metadata")(op => (op.`type`, op.id, op.constant, op.metadata))
}

