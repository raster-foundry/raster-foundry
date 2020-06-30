package com.rasterfoundry.batch.healthcheck

import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster.geotiff.GeoTiffRasterSource

object HealthCheck extends LazyLogging {
  val name = "healthcheck"

  def run(): Unit = {
    logger.info("Batch containers and he batch jar are friends")
  }

  def main(args: Array[String]): Unit = {
    if (args.length > 0) {
      logger.warn(s"Ignoring arguments provided to healthcheck: ${args}")
    }
    this.run
  }
}
