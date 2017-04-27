package com.azavea.rf.tool.ast.codec

import java.security.InvalidParameterException

import com.azavea.rf.tool.ast._
import io.circe._
import io.circe.syntax._

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
}
