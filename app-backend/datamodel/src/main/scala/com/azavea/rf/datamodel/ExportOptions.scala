package com.azavea.rf.datamodel

import java.net.URI

import geotrellis.proj4.CRS
import geotrellis.vector.{MultiPolygon, Projected}
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}

@ConfiguredJsonCodec
final case class ExportOptions(mask: Option[Projected[MultiPolygon]],
                               resolution: Int,
                               crop: Boolean = false,
                               raw: Boolean = false,
                               bands: Option[Seq[Int]],
                               rasterSize: Option[Int],
                               crs: Option[Int],
                               source: URI = new URI(""),
                               operation: String = "id") {
  def render = Render(operation, bands)
  def getCrs: Option[CRS] = crs.map(CRS.fromEpsgCode)
}

object ExportOptions {
  implicit val config: Configuration = Configuration.default.withDefaults
}
