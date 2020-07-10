package com.rasterfoundry.common.utils

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.geotiff.GeoTiffRasterSource
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.vector.Projected
import geotrellis.vector._

object CogUtils extends LazyLogging {

  def getTiffExtent(uri: String): Projected[MultiPolygon] = {
    val rasterSource = GeoTiffRasterSource(uri)
    val crs = rasterSource.crs
    Projected(
      MultiPolygon(rasterSource.extent.reproject(crs, WebMercator).toPolygon()),
      3857
    )
  }

  def histogramFromUri(
      uri: String,
      buckets: Int = 80
  ): Option[Array[Histogram[Double]]] = {
    // use the resolution that's closest to 100,000 pixels and not greater than 400,000 pixels
    // this must fit in a request/response cycle. A 500x500 overview is reasonable, and that adds
    // up to 250,000 pixels
    // We may need to adjust this number depending on how fast our API is able to process it, these
    // numbers are based off local testing
    val rasterSource = GeoTiffRasterSource(uri)
    logger.debug(s"Base cell size is: ${rasterSource.cellSize}")
    val targetCellSize = TargetCellSize(rasterSource.resolutions.sortBy(_.height).max)

    rasterSource
      .resample(targetCellSize, ResampleMethods.NearestNeighbor, OverviewStrategy.DEFAULT)
      .read()
      .map(_.tile.histogramDouble(buckets))
  }
}
