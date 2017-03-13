package com.azavea.rf.tool.ast.codec

import com.azavea.rf.tool.ast._

import geotrellis.raster.render._
import io.circe._
import io.circe.optics.JsonPath._
import io.circe.syntax._
import io.circe.generic.auto._

import scala.util.Try
import java.security.InvalidParameterException


trait MapAlgebraSourceCodecs {
  implicit def mapAlgebraDecoder: Decoder[MapAlgebraAST]
  implicit def mapAlgebraEncoder: Encoder[MapAlgebraAST]

  // Codec routing for Sources
  implicit lazy val decodeSources = Decoder.instance[MapAlgebraAST.Source[_]] { src =>
    src._type match {
      case Some("raster") => src.as[MapAlgebraAST.RFMLRasterSource]
      case Some(unrecognized) =>
        throw new InvalidParameterException(s"'${src.value.noSpaces}' is not a recognized map algebra source")
      case _ =>
        throw new InvalidParameterException(s"THIS SHOULD NEVER RUN. Attempted to decode: '${src.value.noSpaces}'.")
    }
  }

  implicit lazy val encodeSources: Encoder[MapAlgebraAST.Source[_]] =
    new Encoder[MapAlgebraAST.Source[_]] {
      final def apply(src: MapAlgebraAST.Source[_]): Json = src match {
        case rasterSrc: MapAlgebraAST.RFMLRasterSource =>
          rasterSrc.asJson
        case _ =>
          throw new InvalidParameterException(s"THIS SHOULD NEVER RUN. Attempted to decode: '${src}'.")
      }
    }

  implicit lazy val decodeRFMLRasterSource: Decoder[MapAlgebraAST.RFMLRasterSource] =
    Decoder.forProduct3("id", "label", "value")(MapAlgebraAST.RFMLRasterSource.apply _)
  implicit lazy val encodeRFMLRasterSource: Encoder[MapAlgebraAST.RFMLRasterSource] =
    Encoder.forProduct4("type", "id", "label", "value")(op => (op.`type`, op.id, op.label, op.value))

}
