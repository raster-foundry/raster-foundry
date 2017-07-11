package com.azavea.rf.tool.ast.codec

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
import com.azavea.rf.bridge._
import io.circe._
import io.circe.syntax._

import java.security.InvalidParameterException


trait MapAlgebraOperationCodecs {
  implicit def mapAlgebraDecoder: Decoder[MapAlgebraAST]
  implicit def mapAlgebraEncoder: Encoder[MapAlgebraAST]

  // Codec routing for Operations
  implicit lazy val decodeOperations = Decoder.instance[MapAlgebraAST.Operation] { ma =>
    ma._symbol match {
      case Some("+") => ma.as[MapAlgebraAST.Addition]
      case Some("-") => ma.as[MapAlgebraAST.Subtraction]
      case Some("/") => ma.as[MapAlgebraAST.Division]
      case Some("*") => ma.as[MapAlgebraAST.Multiplication]
      case Some("mask") => ma.as[MapAlgebraAST.Masking]
      case Some("min") => ma.as[MapAlgebraAST.Min]
      case Some("max") => ma.as[MapAlgebraAST.Max]
      case Some("classify") => ma.as[MapAlgebraAST.Classification]
      case Some("focalMax") => ma.as[MapAlgebraAST.FocalMax]
      case Some(unrecognized) =>
        Left(DecodingFailure(s"Unrecognized node type: $unrecognized", ma.history))
      case None =>
        Left(DecodingFailure("The property 'apply' is mandatory on all AST operations", ma.history))
    }
  }

  implicit lazy val encodeOperations: Encoder[MapAlgebraAST.Operation] = new Encoder[MapAlgebraAST.Operation] {
    final def apply(op: MapAlgebraAST.Operation): Json = op match {
      case addition: MapAlgebraAST.Addition =>
        addition.asJson
      case subtraction: MapAlgebraAST.Subtraction =>
        subtraction.asJson
      case division: MapAlgebraAST.Division =>
        division.asJson
      case multiplication: MapAlgebraAST.Multiplication =>
        multiplication.asJson
      case masking: MapAlgebraAST.Masking =>
        masking.asJson
      case min: MapAlgebraAST.Min =>
        min.asJson
      case max: MapAlgebraAST.Max =>
        max.asJson
      case classification: MapAlgebraAST.Classification =>
        classification.asJson
      case fMax: MapAlgebraAST.FocalMax =>
        fMax.asJson
      case operation =>
        throw new InvalidParameterException(s"Encoder for $operation not yet implemented")
    }
  }

  /** NOTE: We need to keep these specialized encoder/decoders around for correct parsing of trees */
  implicit lazy val decodeAddition: Decoder[MapAlgebraAST.Addition] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Addition.apply)
  implicit lazy val encodeAddition: Encoder[MapAlgebraAST.Addition] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeSubtraction: Decoder[MapAlgebraAST.Subtraction] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Subtraction.apply)
  implicit lazy val encodeSubtraction: Encoder[MapAlgebraAST.Subtraction] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeDivision: Decoder[MapAlgebraAST.Division] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Division.apply)
  implicit lazy val encodeDivision: Encoder[MapAlgebraAST.Division] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeMultiplication: Decoder[MapAlgebraAST.Multiplication] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Multiplication.apply)
  implicit lazy val encodeMultiplication: Encoder[MapAlgebraAST.Multiplication] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeMasking: Decoder[MapAlgebraAST.Masking] =
    Decoder.forProduct4("args", "id", "metadata", "mask")(MapAlgebraAST.Masking.apply)
  implicit lazy val encodeMasking: Encoder[MapAlgebraAST.Masking] =
    Encoder.forProduct5("apply", "args", "id", "metadata", "mask")(op => (op.symbol, op.args, op.id, op.metadata, op.mask))

  implicit lazy val decodeClassification: Decoder[MapAlgebraAST.Classification] =
    Decoder.forProduct4("args", "id", "metadata", "classMap")(MapAlgebraAST.Classification.apply)
  implicit lazy val encodeClassification: Encoder[MapAlgebraAST.Classification] =
    Encoder.forProduct5("apply", "args", "id", "metadata", "classMap")(op => (op.symbol, op.args, op.id, op.metadata, op.classMap))

  implicit lazy val decodeMax: Decoder[MapAlgebraAST.Max] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Max.apply)
  implicit lazy val encodeMax: Encoder[MapAlgebraAST.Max] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeMin: Decoder[MapAlgebraAST.Min] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Min.apply)
  implicit lazy val encodeMin: Encoder[MapAlgebraAST.Min] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeFocalMax: Decoder[MapAlgebraAST.FocalMax] =
    Decoder.forProduct4("args", "id", "metadata", "neighborhood")(MapAlgebraAST.FocalMax.apply)
  implicit lazy val encodeFocalMax: Encoder[MapAlgebraAST.FocalMax] =
    Encoder.forProduct5("apply", "args", "id", "metadata", "neighborhood")(op => (op.symbol, op.args, op.id, op.metadata, op.neighborhood))
}
