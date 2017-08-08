package com.azavea.rf.tool.ast.codec

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
import com.azavea.rf.bridge._
import io.circe._
import io.circe.syntax._

import java.security.InvalidParameterException


trait MapAlgebraOperationCodecs extends MapAlgebraUtilityCodecs {
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
      case Some("focalMin") => ma.as[MapAlgebraAST.FocalMin]
      case Some("focalMean") => ma.as[MapAlgebraAST.FocalMean]
      case Some("focalMedian") => ma.as[MapAlgebraAST.FocalMedian]
      case Some("focalMode") => ma.as[MapAlgebraAST.FocalMode]
      case Some("focalSum") => ma.as[MapAlgebraAST.FocalSum]
      case Some("focalStdDev") => ma.as[MapAlgebraAST.FocalStdDev]
      case Some("and") => ma.as[MapAlgebraAST.And]
      case Some("not") => ma.as[MapAlgebraAST.LogicalNegation]
      case Some("or") => ma.as[MapAlgebraAST.Or]
      case Some("xor") => ma.as[MapAlgebraAST.Xor]
      case Some("^") => ma.as[MapAlgebraAST.Pow]
      case Some("abs") => ma.as[MapAlgebraAST.Abs]
      case Some(">") => ma.as[MapAlgebraAST.Greater]
      case Some(">=") => ma.as[MapAlgebraAST.GreaterOrEqual]
      case Some("==") => ma.as[MapAlgebraAST.Equality]
      case Some("!=") => ma.as[MapAlgebraAST.Inequality]
      case Some("<") => ma.as[MapAlgebraAST.Less]
      case Some("<=") => ma.as[MapAlgebraAST.LessOrEqual]
      case Some("log") => ma.as[MapAlgebraAST.Log]
      case Some("log10") => ma.as[MapAlgebraAST.Log10]
      case Some("sqrt") => ma.as[MapAlgebraAST.SquareRoot]
      case Some("round") => ma.as[MapAlgebraAST.Round]
      case Some("ceil") => ma.as[MapAlgebraAST.Ceil]
      case Some("floor") => ma.as[MapAlgebraAST.Floor]
      case Some("neg") => ma.as[MapAlgebraAST.NumericNegation]
      case Some("sin") => ma.as[MapAlgebraAST.Sin]
      case Some("cos") => ma.as[MapAlgebraAST.Cos]
      case Some("tan") => ma.as[MapAlgebraAST.Tan]
      case Some("sinh") => ma.as[MapAlgebraAST.Sinh]
      case Some("cosh") => ma.as[MapAlgebraAST.Cosh]
      case Some("tanh") => ma.as[MapAlgebraAST.Tanh]
      case Some("asin") => ma.as[MapAlgebraAST.Asin]
      case Some("acos") => ma.as[MapAlgebraAST.Acos]
      case Some("atan") => ma.as[MapAlgebraAST.Atan]
      case Some("atan2") => ma.as[MapAlgebraAST.Atan2]
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
      case fMin: MapAlgebraAST.FocalMin =>
        fMin.asJson
      case fMean: MapAlgebraAST.FocalMean =>
        fMean.asJson
      case fMedian: MapAlgebraAST.FocalMedian =>
        fMedian.asJson
      case fMode: MapAlgebraAST.FocalMode =>
        fMode.asJson
      case fSum: MapAlgebraAST.FocalSum =>
        fSum.asJson
      case fStdDev: MapAlgebraAST.FocalStdDev =>
        fStdDev.asJson
      case and: MapAlgebraAST.And =>
        and.asJson
      case not: MapAlgebraAST.LogicalNegation =>
        not.asJson
      case or: MapAlgebraAST.Or =>
        or.asJson
      case xor: MapAlgebraAST.Xor =>
        xor.asJson
      case pow: MapAlgebraAST.Pow =>
        pow.asJson
      case abs: MapAlgebraAST.Abs =>
        abs.asJson
      case defined: MapAlgebraAST.IsDefined =>
        defined.asJson
      case undefined: MapAlgebraAST.IsUndefined =>
        undefined.asJson
      case greater: MapAlgebraAST.Greater =>
        greater.asJson
      case greaterOrEqual: MapAlgebraAST.GreaterOrEqual =>
        greaterOrEqual.asJson
      case equal: MapAlgebraAST.Equality =>
        equal.asJson
      case unequal: MapAlgebraAST.Inequality =>
        unequal.asJson
      case less: MapAlgebraAST.Less =>
        less.asJson
      case lessOrEqual: MapAlgebraAST.LessOrEqual =>
        lessOrEqual.asJson
      case log: MapAlgebraAST.Log =>
        log.asJson
      case log10: MapAlgebraAST.Log10 =>
        log10.asJson
      case sqrt: MapAlgebraAST.SquareRoot =>
        sqrt.asJson
      case round: MapAlgebraAST.Round =>
        round.asJson
      case ceil: MapAlgebraAST.Ceil =>
        ceil.asJson
      case floor: MapAlgebraAST.Floor =>
        floor.asJson
      case negative: MapAlgebraAST.NumericNegation =>
        negative.asJson
      case sin: MapAlgebraAST.Sin =>
        sin.asJson
      case cos: MapAlgebraAST.Cos =>
        cos.asJson
      case tan: MapAlgebraAST.Tan =>
        tan.asJson
      case sinh: MapAlgebraAST.Sinh =>
        sinh.asJson
      case cosh: MapAlgebraAST.Cosh =>
        cosh.asJson
      case tanh: MapAlgebraAST.Tanh =>
        tanh.asJson
      case asin: MapAlgebraAST.Asin =>
        asin.asJson
      case acos: MapAlgebraAST.Acos =>
        acos.asJson
      case atan: MapAlgebraAST.Atan =>
        atan.asJson
      case atan2: MapAlgebraAST.Atan2 =>
        atan2.asJson
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

  implicit lazy val decodeFocalMin: Decoder[MapAlgebraAST.FocalMin] =
    Decoder.forProduct4("args", "id", "metadata", "neighborhood")(MapAlgebraAST.FocalMin.apply)
  implicit lazy val encodeFocalMin: Encoder[MapAlgebraAST.FocalMin] =
    Encoder.forProduct5("apply", "args", "id", "metadata", "neighborhood")(op => (op.symbol, op.args, op.id, op.metadata, op.neighborhood))

  implicit lazy val decodeFocalMean: Decoder[MapAlgebraAST.FocalMean] =
    Decoder.forProduct4("args", "id", "metadata", "neighborhood")(MapAlgebraAST.FocalMean.apply)
  implicit lazy val encodeFocalMean: Encoder[MapAlgebraAST.FocalMean] =
    Encoder.forProduct5("apply", "args", "id", "metadata", "neighborhood")(op => (op.symbol, op.args, op.id, op.metadata, op.neighborhood))

  implicit lazy val decodeFocalMedian: Decoder[MapAlgebraAST.FocalMedian] =
    Decoder.forProduct4("args", "id", "metadata", "neighborhood")(MapAlgebraAST.FocalMedian.apply)
  implicit lazy val encodeFocalMedian: Encoder[MapAlgebraAST.FocalMedian] =
    Encoder.forProduct5("apply", "args", "id", "metadata", "neighborhood")(op => (op.symbol, op.args, op.id, op.metadata, op.neighborhood))

  implicit lazy val decodeFocalMode: Decoder[MapAlgebraAST.FocalMode] =
    Decoder.forProduct4("args", "id", "metadata", "neighborhood")(MapAlgebraAST.FocalMode.apply)
  implicit lazy val encodeFocalMode: Encoder[MapAlgebraAST.FocalMode] =
    Encoder.forProduct5("apply", "args", "id", "metadata", "neighborhood")(op => (op.symbol, op.args, op.id, op.metadata, op.neighborhood))

  implicit lazy val decodeFocalSum: Decoder[MapAlgebraAST.FocalSum] =
    Decoder.forProduct4("args", "id", "metadata", "neighborhood")(MapAlgebraAST.FocalSum.apply)
  implicit lazy val encodeFocalSum: Encoder[MapAlgebraAST.FocalSum] =
    Encoder.forProduct5("apply", "args", "id", "metadata", "neighborhood")(op => (op.symbol, op.args, op.id, op.metadata, op.neighborhood))

  implicit lazy val decodeFocalStdDev: Decoder[MapAlgebraAST.FocalStdDev] =
    Decoder.forProduct4("args", "id", "metadata", "neighborhood")(MapAlgebraAST.FocalStdDev.apply)
  implicit lazy val encodeFocalStdDev: Encoder[MapAlgebraAST.FocalStdDev] =
    Encoder.forProduct5("apply", "args", "id", "metadata", "neighborhood")(op => (op.symbol, op.args, op.id, op.metadata, op.neighborhood))

  implicit lazy val decodeAnd: Decoder[MapAlgebraAST.And] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.And.apply)
  implicit lazy val encodeAnd: Encoder[MapAlgebraAST.And] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeLogicalNegation: Decoder[MapAlgebraAST.LogicalNegation] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.LogicalNegation.apply)
  implicit lazy val encodeLogicalNegation: Encoder[MapAlgebraAST.LogicalNegation] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeLogicalDisjunction: Decoder[MapAlgebraAST.Or] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Or.apply)
  implicit lazy val encodeLogicalDisjunction: Encoder[MapAlgebraAST.Or] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeLogicallyExclusiveDisjunction: Decoder[MapAlgebraAST.Xor] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Xor.apply)
  implicit lazy val encodeLogicallyExclusiveDisjunction: Encoder[MapAlgebraAST.Xor] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodePow: Decoder[MapAlgebraAST.Pow] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Pow.apply)
  implicit lazy val encodePow: Encoder[MapAlgebraAST.Pow] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeAbs: Decoder[MapAlgebraAST.Abs] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Abs.apply)
  implicit lazy val encodeAbs: Encoder[MapAlgebraAST.Abs] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeGreater: Decoder[MapAlgebraAST.Greater] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Greater.apply)
  implicit lazy val encodeGreater: Encoder[MapAlgebraAST.Greater] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeGreaterOrEqual: Decoder[MapAlgebraAST.GreaterOrEqual] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.GreaterOrEqual.apply)
  implicit lazy val encodeGreaterOrEqual: Encoder[MapAlgebraAST.GreaterOrEqual] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeEquality: Decoder[MapAlgebraAST.Equality] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Equality.apply)
  implicit lazy val encodeEquality: Encoder[MapAlgebraAST.Equality] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeInequality: Decoder[MapAlgebraAST.Inequality] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Inequality.apply)
  implicit lazy val encodeInequality: Encoder[MapAlgebraAST.Inequality] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeLess: Decoder[MapAlgebraAST.Less] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Less.apply)
  implicit lazy val encodeLess: Encoder[MapAlgebraAST.Less] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeLessOrEqual: Decoder[MapAlgebraAST.LessOrEqual] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.LessOrEqual.apply)
  implicit lazy val encodeLessOrEqual: Encoder[MapAlgebraAST.LessOrEqual] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeLog: Decoder[MapAlgebraAST.Log] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Log.apply)
  implicit lazy val encodeLog: Encoder[MapAlgebraAST.Log] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeLog10: Decoder[MapAlgebraAST.Log10] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Log10.apply)
  implicit lazy val encodeLog10: Encoder[MapAlgebraAST.Log10] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeSquareRoot: Decoder[MapAlgebraAST.SquareRoot] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.SquareRoot.apply)
  implicit lazy val encodeSquareRoot: Encoder[MapAlgebraAST.SquareRoot] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeRound: Decoder[MapAlgebraAST.Round] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Round.apply)
  implicit lazy val encodeRound: Encoder[MapAlgebraAST.Round] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeCeil: Decoder[MapAlgebraAST.Ceil] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Ceil.apply)
  implicit lazy val encodeCeil: Encoder[MapAlgebraAST.Ceil] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeFloor: Decoder[MapAlgebraAST.Floor] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Floor.apply)
  implicit lazy val encodeFloor: Encoder[MapAlgebraAST.Floor] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeNumericNegation: Decoder[MapAlgebraAST.NumericNegation] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.NumericNegation.apply)
  implicit lazy val encodeNumericNegation: Encoder[MapAlgebraAST.NumericNegation] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeSin: Decoder[MapAlgebraAST.Sin] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Sin.apply)
  implicit lazy val encodeSin: Encoder[MapAlgebraAST.Sin] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeCos: Decoder[MapAlgebraAST.Cos] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Cos.apply)
  implicit lazy val encodeCos: Encoder[MapAlgebraAST.Cos] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeTan: Decoder[MapAlgebraAST.Tan] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Tan.apply)
  implicit lazy val encodeTan: Encoder[MapAlgebraAST.Tan] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeSinh: Decoder[MapAlgebraAST.Sinh] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Sinh.apply)
  implicit lazy val encodeSinh: Encoder[MapAlgebraAST.Sinh] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeCosh: Decoder[MapAlgebraAST.Cosh] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Cosh.apply)
  implicit lazy val encodeCosh: Encoder[MapAlgebraAST.Cosh] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeTanh: Decoder[MapAlgebraAST.Tanh] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Tanh.apply)
  implicit lazy val encodeTanh: Encoder[MapAlgebraAST.Tanh] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeAsin: Decoder[MapAlgebraAST.Asin] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Asin.apply)
  implicit lazy val encodeAsin: Encoder[MapAlgebraAST.Asin] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeAcos: Decoder[MapAlgebraAST.Acos] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Acos.apply)
  implicit lazy val encodeAcos: Encoder[MapAlgebraAST.Acos] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeAtan: Decoder[MapAlgebraAST.Atan] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Atan.apply)
  implicit lazy val encodeAtan: Encoder[MapAlgebraAST.Atan] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeAtan2: Decoder[MapAlgebraAST.Atan2] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.Atan2.apply)
  implicit lazy val encodeAtan2: Encoder[MapAlgebraAST.Atan2] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeIsDefined: Decoder[MapAlgebraAST.IsDefined] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.IsDefined.apply)
  implicit lazy val encodeIsDefined: Encoder[MapAlgebraAST.IsDefined] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))

  implicit lazy val decodeIsUndefined: Decoder[MapAlgebraAST.IsUndefined] =
    Decoder.forProduct3("args", "id", "metadata")(MapAlgebraAST.IsUndefined.apply)
  implicit lazy val encodeIsUndefined: Encoder[MapAlgebraAST.IsUndefined] =
    Encoder.forProduct4("apply", "args", "id", "metadata")(op => (op.symbol, op.args, op.id, op.metadata))
}
