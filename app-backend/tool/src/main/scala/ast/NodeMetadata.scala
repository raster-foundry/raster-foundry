package com.azavea.rf.tool.ast

import com.azavea.rf.tool.ast.codec.MapAlgebraCodec
import geotrellis.raster.histogram._
import geotrellis.raster.render._
import io.circe._


case class NodeMetadata(
  label: Option[String] = None,
  description: Option[String] = None,
  histogram: Option[Histogram[Double]] = None,
  colorRamp: Option[ColorRamp] = None,
  classMap: Option[ClassMap] = None
) {

  /** A helper method for merging default values with overrides */
  def fallbackTo(that: NodeMetadata): NodeMetadata = {
    NodeMetadata(
      this.label.orElse(that.label),
      this.description.orElse(that.description),
      this.histogram.orElse(that.histogram),
      this.colorRamp.orElse(that.colorRamp),
      this.classMap.orElse(that.classMap)
    )
  }
}

object NodeMetadata extends MapAlgebraCodec {
  implicit val nodeMetadataEncoder: Encoder[NodeMetadata] =
    Encoder.forProduct5("label", "description", "histogram", "colorRamp", "classMap")(nmd =>
      (nmd.label, nmd.description, nmd.histogram, nmd.colorRamp, nmd.classMap)
    )
  implicit val nodeMetadataDecoder: Decoder[NodeMetadata] =
    Decoder.forProduct5("label", "description", "histogram", "colorRamp", "classMap")(NodeMetadata.apply)
}
