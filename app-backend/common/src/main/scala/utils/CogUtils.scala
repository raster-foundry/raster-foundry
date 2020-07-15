package com.rasterfoundry.common.utils

import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.geotiff.GeoTiffRasterSource
import geotrellis.vector.Projected
import geotrellis.vector._

object CogUtils extends LazyLogging {

  def getTiffExtent(uri: String): Projected[MultiPolygon] = {
    val rasterSource = GeoTiffRasterSource(uri)
    Projected(
      MultiPolygon(
        rasterSource.extent
          .reproject(rasterSource.crs, WebMercator)
          .toPolygon()),
      3857
    )
  }

  def histogramFromUri(
      uri: String,
      buckets: Int = 80
  ): Option[Array[Histogram[Double]]] = {
    // Get the smallest overview and calculate histogram from that
    val rasterSource = GeoTiffRasterSource(uri)
    val bandCount = rasterSource.bandCount
    logger.debug(s"Base cell size is: ${rasterSource.cellSize}")
    // This is to workaround https://github.com/locationtech/geotrellis/issues/3269
    // where not all overviews are actually overviews
    val lastOverview =
      rasterSource.tiff.overviews.filter(_.bandCount == bandCount).lastOption
    lastOverview.map(_.tile.bands.map(_.histogramDouble(buckets)).toArray)
  }
}
