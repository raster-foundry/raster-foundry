package com.azavea.rf.tool.ast

import geotrellis.raster.render.ColorRamp
import geotrellis.raster.histogram._
import io.circe._
import io.circe.syntax._
import io.circe.disjunctionCodecs._
import io.circe.generic.JsonCodec

import com.azavea.rf.tool.ast.codec.MapAlgebraCodec


case class NodeMetadata(
  label: Option[String],
  description: Option[String],
  histogram: Option[Histogram[Double]]
)

object NodeMetadata extends MapAlgebraCodec {
  implicit val nodeMetadataEncoder: Encoder[NodeMetadata] =
    Encoder.forProduct3("label", "description", "histogram")(nmd =>
      (nmd.label, nmd.description, nmd.histogram)
    )
  implicit val nodeMetadataDecoder: Decoder[NodeMetadata] =
    Decoder.forProduct3("label", "description", "histogram")(NodeMetadata.apply)
}
