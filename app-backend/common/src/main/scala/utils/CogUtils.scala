package com.rasterfoundry.common.utils

import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.geotiff.GeoTiffRasterSource
import geotrellis.vector.Projected
import geotrellis.vector._

object CogUtils extends LazyLogging {

  def getTiffExtent(
      rasterSource: GeoTiffRasterSource
  ): Projected[MultiPolygon] = {
    Projected(
      MultiPolygon(
        rasterSource.extent
          .reproject(rasterSource.crs, WebMercator)
          .toPolygon()
      ),
      3857
    )
  }

  def histogramFromUri(
      rasterSource: GeoTiffRasterSource,
      buckets: Int = 80
  ): Option[Array[Histogram[Double]]] = {
    // Get the smallest overview and calculate histogram from that
    rasterSource.tiff.overviews.lastOption
      .map(_.tile.bands.map(_.histogramDouble(buckets)).toArray)
  }
}
