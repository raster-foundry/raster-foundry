package com.rasterfoundry.backsplash.color

import com.rasterfoundry.common.datamodel.ColorComposite

import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.server.ogc.{OutputFormat, OgcStyle}

object OgcStyles {

  private def toBytes(mbt: MultibandTile,
                      outputFormat: OutputFormat): Array[Byte] =
    outputFormat match {
      case OutputFormat.Png => mbt.renderPng.bytes
      case OutputFormat.Jpg => mbt.renderJpg.bytes
      // Not implementable without an extent, I think
      case OutputFormat.GeoTiff => ???
    }

  def fromColorComposite(colorComposite: ColorComposite): OgcStyle =
    new OgcStyle {
      val name = colorComposite.label
      val title = colorComposite.label
      def renderImage(mbtile: MultibandTile,
                      format: OutputFormat,
                      hists: List[Histogram[Double]]): Array[Byte] = {
        val bands = List(colorComposite.value.redBand,
                         colorComposite.value.greenBand,
                         colorComposite.value.blueBand)
        val rgbHists = bands map { hists(_) }
        val subset = mbtile.subsetBands(bands)
        val params =
          ColorCorrect.paramsFromBandSpecOnly(0, 1, 2)
        val corrected = params.colorCorrect(subset,
                                            rgbHists.toSeq,
                                            getNoDataValue(subset.cellType))
        toBytes(corrected, format)
      }
    }

  def fromSingleBandOptions(singleBandParams: SingleBandOptions.Params,
                            layerName: String): OgcStyle = new OgcStyle {
    val name = s"$layerName - ${singleBandParams.band}"
    val title = s"$layerName - ${singleBandParams.band}"
    def renderImage(mbtile: MultibandTile,
                    format: OutputFormat,
                    hists: List[Histogram[Double]]): Array[Byte] = {
      val tile = mbtile.subsetBands(singleBandParams.band)
      val hist = List(hists(singleBandParams.band))
      val colored = ColorRampMosaic.colorTile(tile, hist, singleBandParams)
      toBytes(colored, format)
    }
  }
}
