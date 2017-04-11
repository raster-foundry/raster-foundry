package com.azavea.rf.datamodel

import geotrellis.slick.Projected
import geotrellis.vector.MultiPolygon
import geotrellis.proj4.CRS

import io.circe.generic.JsonCodec
import java.net.URI

@JsonCodec
case class ExportOptions(
  mask: Option[Projected[MultiPolygon]],
  resolution: Int,
  stitch: Boolean,
  crop: Boolean,
  bands: Option[Seq[Int]],
  rasterSize: Option[Int],
  crs: Option[Int],
  source: URI
) {
  def render = Render("id", bands)
  def getCrs = crs.map(CRS.fromEpsgCode)
}
