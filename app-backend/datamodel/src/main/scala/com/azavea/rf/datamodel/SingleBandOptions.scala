package com.azavea.rf.datamodel

import com.azavea.rf.datamodel.color._

import io.circe.generic.JsonCodec
import io.circe.Json
import geotrellis.raster._
import geotrellis.raster.equalization.HistogramEqualization
import geotrellis.raster.histogram.Histogram
import org.apache.commons.math3.util.FastMath
import spire.syntax.cfor._

object SingleBandOptions {

  @JsonCodec
  case class Params(
    band: Int,
    dataType: BandDataType,
    colorBins: Int,
    colorScheme: Json,
    legendOrientation: String
  )
}
