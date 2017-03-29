package com.azavea.rf.tool.ast

import geotrellis.raster.render._
import geotrellis.raster._
import geotrellis.util._
import io.circe.generic.JsonCodec

@JsonCodec
case class ClassBreaks(
  classMap: Map[Double, Int],
  boundaryType: ClassBoundaryType = LessThanOrEqualTo,
  ndValue: Int = NODATA,
  fallback: Int = NODATA
) {
  lazy val mapStrategy =
    new MapStrategy(boundaryType, ndValue, fallback, false)

  def toBreakMap =
    new BreakMap(classMap, mapStrategy, { i: Double => isNoData(i) })
}
