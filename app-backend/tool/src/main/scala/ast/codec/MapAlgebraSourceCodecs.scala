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

  implicit lazy val decodeRFMLRasterSource: Decoder[MapAlgebraAST.RFMLRasterSource] =
    Decoder.forProduct3("id", "label", "value")(MapAlgebraAST.RFMLRasterSource.apply _)
  implicit lazy val encodeRFMLRasterSource: Encoder[MapAlgebraAST.RFMLRasterSource] =
    Encoder.forProduct4("type", "id", "label", "value")(op => (op.`type`, op.id, op.label, op.value))

}
