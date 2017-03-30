package com.azavea.rf.export.model

import geotrellis.proj4.CRS

import spray.json.DefaultJsonProtocol._

import java.net.URI

/**
  * Output definition
  * @param crs output [[CRS]]
  * @param rasterSize output size of each raster chunk
  * @param render [[Render]] options
  * @param crop crop result rasters
  * @param stitch stitch result raster into one
  * @param source output source [[URI]]
  */
case class OutputDefinition(
  crs: Option[CRS],
  rasterSize: Option[Int],
  render: Option[Render],
  crop: Boolean,
  stitch: Boolean,
  source: URI
)

object OutputDefinition {
  implicit val jsonFormat = jsonFormat6(OutputDefinition.apply _)
}
