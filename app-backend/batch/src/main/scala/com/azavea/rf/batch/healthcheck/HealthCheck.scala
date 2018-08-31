package com.azavea.rf.batch.healthcheck

import com.typesafe.scalalogging.LazyLogging

object HealthCheck extends LazyLogging {
  val name = "healthcheck"

  def run(): Unit = {
    logger.info("Batch containers and the batch jar are friends")
  }

  def main(args: Array[String]): Unit = {
    if (args.length > 0) {
      logger.warn(s"Ignoring arguments provided to healthcheck: ${args}")
    }
    this.run
  }
}
