package com.azavea.rf.datamodel

import geotrellis.proj4.CRS
import io.circe.generic.JsonCodec

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
@JsonCodec
case class OutputDefinition(
  crs: Option[CRS],
  rasterSize: Option[Int],
  render: Option[Render],
  crop: Boolean,
  stitch: Boolean,
  source: URI
)
