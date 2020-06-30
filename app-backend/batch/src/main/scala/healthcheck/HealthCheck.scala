package com.rasterfoundry.batch.healthcheck

import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster.geotiff.GeoTiffRasterSource

object HealthCheck extends LazyLogging {
  val name = "healthcheck"

  def run(): Unit = {
    val rs = GeoTiffRasterSource("s3://rasterfoundry-development-data-us-east-1/test.tif")
    rs.tiff
    logger.info("The batch jar can run and can read tifs")
  }

  def main(args: Array[String]): Unit = {
    if (args.length > 0) {
      logger.warn(s"Ignoring arguments provided to healthcheck: ${args}")
    }
    this.run
  }
}
