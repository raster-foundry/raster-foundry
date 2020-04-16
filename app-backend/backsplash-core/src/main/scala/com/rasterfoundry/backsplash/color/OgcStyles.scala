package com.rasterfoundry.backsplash.color

import com.rasterfoundry.common.color.ColorCorrect
import com.rasterfoundry.datamodel.{ColorComposite, SingleBandOptions}

import geotrellis.raster._
import geotrellis.server.ogc.OutputFormat.Png
import geotrellis.server.ogc.{OgcStyle, OutputFormat}

object OgcStyles {

  private def toBytes(mbt: MultibandTile,
                      outputFormat: OutputFormat): Array[Byte] =
    outputFormat match {
      case Png(_)           => mbt.renderPng.bytes
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
        val bands = Seq(colorComposite.value.redBand,
                        colorComposite.value.greenBand,
                        colorComposite.value.blueBand)
        val indexedHist = hists.toIndexedSeq
        val rgbHists = bands map { indexedHist(_) }
        val subset = mbtile.subsetBands(bands)
        val params =
          ColorCorrect.Params(0, 1, 2)
        val corrected = params.colorCorrect(subset, rgbHists.toSeq, None)
        toBytes(corrected, format)
      }

      def legends = Nil
    }

  def fromSingleBandOptions(singleBandParams: SingleBandOptions.Params,
                            layerName: String,
                            indexBand: Boolean = true): OgcStyle =
    new OgcStyle {
      val name = if (indexBand) {
        s"$layerName - ${singleBandParams.band}"
      } else {
        layerName
      }
      val title = if (indexBand) {
        s"$layerName - ${singleBandParams.band}"
      } else {
        layerName
      }
      def renderImage(mbtile: MultibandTile,
                      format: OutputFormat,
                      hists: List[Histogram[Double]]): Array[Byte] = {
        val tile = mbtile.subsetBands(singleBandParams.band)
        val hist = List(hists.toIndexedSeq(singleBandParams.band))
        val colored = ColorRampMosaic.colorTile(tile, hist, singleBandParams)
        toBytes(colored, format)
      }

      def legends = Nil
    }
}
