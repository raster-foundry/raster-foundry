package com.azavea.rf.datamodel

import geotrellis.slick.Projected
import geotrellis.vector.MultiPolygon

import io.circe._
import io.circe.generic.auto._

case class ExportOptions(
  mask: Projected[MultiPolygon],
  stitch: Boolean,
  crop: Boolean,
  bands: Seq[Int],
  rasterSize: Int,
  crs: Int
)
