package com.rasterfoundry.backsplash.color

import com.rasterfoundry.datamodel.ColorComposite

import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.server.ogc.{OutputFormat, StyleModel}

object StyleModels {

  private def toBytes(mbt: MultibandTile, outputFormat: OutputFormat): Array[Byte] =
    format match {
      case OutputFormat.Png => mbt.renderPng.bytes
      case OutputFormat.Jpg => mbt.renderJpg.bytes
      // Not implementable without an extent, I think
      case OutputFormat.GeoTiff => ???
    }

  def fromColorComposite(colorComposite: ColorComposite): StyleModel = new StyleModel {
    val name = colorComposite.label
    val title = colorComposite.label
    def renderImage(mbtile: MultibandTile, format: OutputFormat, hists: List[Histogram[Double]]): Array[Byte] = {
      val bands = List(colorComposite.bandOverride.redBand, colorComposite.bandOverride.greenBand, colorComposite.bandOverride.blueBand)
      val rgbHists = bands map { hists(_) }
      val subset = mbtile.subsetBands(bands)
      val params = ColorCorrect.paramsFromBandSpecOnly(bands: _*)
      val corrected = params.colorCorrect(subset, rgbHists.toSeq, None)
      toBytes(corrected, format)
    }
  }

  def fromSingleBandOptions(singleBandParams: SingleBandOptions.Params, layerName: String): StyleModel = new StyleModel {
    val name = s"$layerName - ${singleBandParams.band}"
    val title = s"$layerName - ${singleBandParams.band}"
    def renderImage(mbtile: MultibandTile, format: OutputFormat, hists: List[Histogram[Double]]): Array[Byte] = {
      val tile = mbtile.subsetBands(singleBandParams.band)
      val hist = List(hists(singleBandOptions.band))
      val colored = ColorRampMosaic.colorTile(tile, hist, singleBandParams)
      toBytes(colored, format)
    }
}
