package com.azavea.rf.common.utils

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.crop._
import geotrellis.raster.resample._
import geotrellis.raster.reproject._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.reader.GeoTiffReader

object GeoTiffUtils {
  /** Work around GeoTiff.closestTiffOverview being private to geotrellis */
  def closestTiffOverview[T <: CellGrid](tiff: GeoTiff[T], cs: CellSize, strategy: OverviewStrategy): GeoTiff[T] = {
    geotrellis.hack.GTHack.closestTiffOverview(tiff, cs, strategy)
  }

  /** Work around bug in GeoTiff.crop(extent) method */
  def cropGeoTiff[T <: CellGrid](tiff: GeoTiff[T], extent: Extent): Raster[T] = {
    val bounds = tiff.rasterExtent.gridBoundsFor(extent)
    val clipExtent = tiff.rasterExtent.extentFor(bounds)
    val clip = tiff.crop(List(bounds)).next._2
    Raster(clip, clipExtent)
  }

  def geoTiffHistogram(tiff: GeoTiff[MultibandTile], buckets: Int = 80, size: Int = 128): Array[StreamingHistogram] = {
    def diagonal(tiff:
                 GeoTiff[MultibandTile]): Int =
      math.sqrt(tiff.cols*tiff.cols + tiff.rows*tiff.rows).toInt

    val goldyLocksOverviews = tiff.overviews.filter{ tiff =>
      val d = diagonal(tiff)
      (d >= size && d <= size*4)
    }

    if (goldyLocksOverviews.nonEmpty){
      // case: overview that is close enough to the size, not more than 4x larger
      // -- read the overview and get histogram
      val theOne = goldyLocksOverviews.minBy(diagonal)
      val hists = Array.fill(tiff.bandCount)(new StreamingHistogram(buckets))
      theOne.tile.foreachDouble{ (band, v) => hists(band).countItem(v, 1) }
      hists
    } else {
      // case: such oveview can't be found
      // -- take min overview and sample window from center
      val theOne = tiff.overviews.minBy(diagonal)
      val sampleBounds = {
        val side = math.sqrt(size*size/2)
        val centerCol = theOne.cols / 2
        val centerRow = theOne.rows / 2
        GridBounds(
          colMin = math.max(0, centerCol - (side / 2)).toInt,
          rowMin = math.max(0, centerRow - (side / 2)).toInt,
          colMax = math.min(theOne.cols - 1, centerCol + (side / 2)).toInt,
          rowMax = math.min(theOne.rows - 1, centerRow + (side / 2)).toInt
        )
      }
      val sample = theOne.crop(List(sampleBounds)).next._2
      val hists = Array.fill(tiff.bandCount)(new StreamingHistogram(buckets))
      sample.foreachDouble{ (band, v) => hists(band).countItem(v, 1) }
      hists
    }
  }
}