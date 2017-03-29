package com.azavea.rf.tool.ast.codec

import com.azavea.rf.tool.ast._

import geotrellis.raster.render._
import io.circe._
import io.circe.optics.JsonPath._
import io.circe.syntax._

import scala.util.Try
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
      case Some("classify") => ma.as[MapAlgebraAST.Classification]
      case Some(unrecognized) =>
        throw new InvalidParameterException(s"'$unrecognized' is not a recognized map algebra operation")
      case None =>
        throw new InvalidParameterException(s"Required 'apply' property not found on MapAlgebraAST operation ${ma.value}")
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
      case classification: MapAlgebraAST.Classification =>
        classification.asJson
      case operation =>
        throw new InvalidParameterException(s"Encoder for $operation not yet implemented")
    }
  }

  // Codec instances
  implicit lazy val decodeAddition: Decoder[MapAlgebraAST.Addition] =
    Decoder.forProduct3("args", "id", "label")(MapAlgebraAST.Addition.apply)
  implicit lazy val encodeAddition: Encoder[MapAlgebraAST.Addition] =
    Encoder.forProduct4("apply", "args", "id", "label")(op => (op.symbol, op.args, op.id, op.label))

  implicit lazy val decodeSubtraction: Decoder[MapAlgebraAST.Subtraction] =
    Decoder.forProduct3("args", "id", "label")(MapAlgebraAST.Subtraction.apply)
  implicit lazy val encodeSubtraction: Encoder[MapAlgebraAST.Subtraction] =
    Encoder.forProduct4("apply", "args", "id", "label")(op => (op.symbol, op.args, op.id, op.label))

  implicit lazy val decodeDivision: Decoder[MapAlgebraAST.Division] =
    Decoder.forProduct3("args", "id", "label")(MapAlgebraAST.Division.apply)
  implicit lazy val encodeDivision: Encoder[MapAlgebraAST.Division] =
    Encoder.forProduct4("apply", "args", "id", "label")(op => (op.symbol, op.args, op.id, op.label))

  implicit lazy val decodeMultiplication: Decoder[MapAlgebraAST.Multiplication] =
    Decoder.forProduct3("args", "id", "label")(MapAlgebraAST.Multiplication.apply)
  implicit lazy val encodeMultiplication: Encoder[MapAlgebraAST.Multiplication] =
    Encoder.forProduct4("apply", "args", "id", "label")(op => (op.symbol, op.args, op.id, op.label))

  implicit lazy val decodeMasking: Decoder[MapAlgebraAST.Masking] =
    Decoder.forProduct3("args", "id", "label")(MapAlgebraAST.Masking.apply)
  implicit lazy val encodeMasking: Encoder[MapAlgebraAST.Masking] =
    Encoder.forProduct4("apply", "args", "id", "label")(op => (op.symbol, op.args, op.id, op.label))

  implicit lazy val decodeClassification: Decoder[MapAlgebraAST.Classification] =
    Decoder.forProduct4("args", "id", "label", "classBreaks")(MapAlgebraAST.Classification.apply _)
  implicit lazy val encodeClassification: Encoder[MapAlgebraAST.Classification] =
    Encoder.forProduct5("apply", "args", "id", "label", "classBreaks")(op => (op.symbol, op.args, op.id, op.label, op.classBreaks))

}
