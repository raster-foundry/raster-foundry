package com.azavea.rf.tool.ast

import geotrellis.raster.render._
import io.circe._
import io.circe.optics.JsonPath._
import io.circe._
import io.circe.generic.auto._

import scala.util.Try
import java.security.InvalidParameterException

object MapAlgebraASTCodec {

  implicit lazy val decodeAddition: Decoder[MapAlgebraAST.Addition] =
    Decoder.forProduct3("args", "id", "label")(MapAlgebraAST.Addition.apply)

  implicit lazy val decodeSubtraction: Decoder[MapAlgebraAST.Subtraction] =
    Decoder.forProduct3("args", "id", "label")(MapAlgebraAST.Subtraction.apply)

  implicit lazy val decodeDivision: Decoder[MapAlgebraAST.Division] =
    Decoder.forProduct3("args", "id", "label")(MapAlgebraAST.Division.apply)

  implicit lazy val decodeMultiplication: Decoder[MapAlgebraAST.Multiplication] =
    Decoder.forProduct3("args", "id", "label")(MapAlgebraAST.Multiplication.apply)

  implicit lazy val decodeMasking: Decoder[MapAlgebraAST.Masking] =
    Decoder.forProduct3("args", "id", "label")(MapAlgebraAST.Masking.apply)

  implicit lazy val decodeReclassification: Decoder[MapAlgebraAST.Reclassification] =
    Decoder.forProduct4("args", "id", "label", "classBreaks")(MapAlgebraAST.Reclassification.apply _)

  implicit lazy val decodeOperations = Decoder.instance[MapAlgebraAST.Operation] { ma =>
    ma._symbol match {
      case Some("+") => ma.as[MapAlgebraAST.Addition]
      case Some("-") => ma.as[MapAlgebraAST.Subtraction]
      case Some("/") => ma.as[MapAlgebraAST.Division]
      case Some("*") => ma.as[MapAlgebraAST.Multiplication]
      case Some("mask") => ma.as[MapAlgebraAST.Multiplication]
      case Some("reclassify") => ma.as[MapAlgebraAST.Multiplication]
      case Some(unrecognized) =>
        throw new InvalidParameterException(s"'$unrecognized' is not a recognized map algebra operation")
      case None =>
        throw new InvalidParameterException(s"Required 'apply' property not found on MapAlgebraAST operation")
    }
  }

  implicit lazy val decodeMapAlgebraAST = Decoder.instance[MapAlgebraAST] { ma =>
    ma._type match {
      case Some("raster") =>
        ma.as[MapAlgebraAST.RFMLRasterSource]
      //case Some("vector") =>
      //  ma.as[MapAlgebraAST.VectorSource]
      //case Some("double") =>
      //  ma.as[MapAlgebraAST.DecimalSource]
      //case Some("int") =>
      //  ma.as[MapAlgebraAST.IntegralSource]
      case Some(unrecognized) =>
        throw new InvalidParameterException(s"'$unrecognized' is not a recognized map algebra data type")
      case None =>
        ma.as[MapAlgebraAST.Operation]
    }
  }
}
