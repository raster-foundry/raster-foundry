package com.azavea.rf.datamodel

import com.azavea.rf.bridge._
import geotrellis.slick.Projected
import geotrellis.vector.MultiPolygon
import geotrellis.proj4.CRS
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}

import java.net.URI

@ConfiguredJsonCodec
case class ExportOptions(
  mask: Option[Projected[MultiPolygon]],
  resolution: Int,
  stitch: Boolean = false,
  crop: Boolean = false,
  raw: Boolean = false,
  bands: Option[Seq[Int]],
  rasterSize: Option[Int],
  crs: Option[Int],
  source: URI = new URI(""),
  operation: String = "id"
) {
  def render = Render(operation, bands)
  def getCrs = crs.map(CRS.fromEpsgCode)
}

object ExportOptions {
  implicit val config: Configuration = Configuration.default.withDefaults
}
