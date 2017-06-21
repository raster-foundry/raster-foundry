package com.azavea.rf.datamodel

import com.azavea.rf.bridge._
import geotrellis.proj4.CRS
import io.circe.generic.JsonCodec

import java.net.{URI, URLDecoder}

/**
  * Output definition
  * @param crs output [[CRS]]
  * @param rasterSize output size of each raster chunk
  * @param render [[Render]] options
  * @param crop crop result rasters
  * @param stitch stitch result raster into one
  * @param source output source [[URI]]
  * @param dropboxCredential dropbox token
  */
@JsonCodec
case class OutputDefinition(
  crs: Option[CRS],
  rasterSize: Option[Int],
  render: Option[Render],
  crop: Boolean,
  stitch: Boolean,
  source: URI,
  dropboxCredential: Option[String]
) {
  def getURLDecodedSource: String =
    URLDecoder.decode(source.toString, "UTF-8").replace("dropbox:///", "")
}
