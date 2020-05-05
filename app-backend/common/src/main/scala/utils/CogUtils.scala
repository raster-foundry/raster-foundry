package com.rasterfoundry.common.utils

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.geotiff.GeoTiffRasterSource
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

  private def viableCellSizes(
      rs: RasterSource,
      baseCellSize: CellSize,
      sizes: List[CellSize],
      target: Long
  ): List[CellSize] = {
    val width = rs.cols
    val height = rs.rows

    val isGood: CellSize => Boolean = (cs: CellSize) => {
      val cellCount = width / (cs.width / baseCellSize.width) * (height / (cs.height / baseCellSize.height))
      logger.debug(s"Cell count for $cs: $cellCount")
      cellCount < target
    }

    sizes.filter(isGood)
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
    val viable = viableCellSizes(
      rasterSource,
      rasterSource.cellSize,
      rasterSource.resolutions,
      400000
    )
    logger.debug(s"Number of viable cell sizes: ${viable.size}")
    viable.toNel
      .flatMap(
        resNel =>
          rasterSource
            .resampleToGrid(
              rasterSource.gridExtent.withResolution(
                resNel.toList.minBy(
                  r =>
                    scala.math.abs(
                      100000 - (rasterSource.rows / r.height) * (rasterSource.cols / r.width)
                    )
                )
              )
            )
            .read()
            .map(_.tile.histogramDouble(buckets))
      )
  }
}
