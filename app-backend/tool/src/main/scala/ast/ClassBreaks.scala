package com.azavea.rf.tool.ast

import geotrellis.raster.render._

import io.circe.generic.JsonCodec

@JsonCodec
case class ClassBreaks(classMap: Map[Double, Double], boundaryType: ClassBoundaryType = LessThanOrEqualTo)

