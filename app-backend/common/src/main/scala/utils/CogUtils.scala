package com.rasterfoundry.common.utils

import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster._
import geotrellis.raster.gdal.GDALRasterSource
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.raster.resample.ResampleMethod
import geotrellis.vector.Projected
import geotrellis.vector._

object CogUtils extends LazyLogging {

  def getTiffExtent(
      rasterSource: GDALRasterSource
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
      rasterSource: GDALRasterSource,
      buckets: Int = 80
  ): Option[Array[Histogram[Double]]] = {
    val largestCellSize = rasterSource.resolutions
      .maxBy(_.resolution)

    val resampleTarget = TargetCellSize(largestCellSize)
    rasterSource
      .resample(
        resampleTarget,
        ResampleMethod.DEFAULT,
        OverviewStrategy.DEFAULT
      )
      .read
      .map(_.tile.bands.map(_.histogramDouble(buckets)).toArray)

  }
}
