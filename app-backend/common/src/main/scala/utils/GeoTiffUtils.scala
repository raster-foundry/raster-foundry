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
}