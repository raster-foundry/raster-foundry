package com.azavea.rf.batch.healthcheck

import com.typesafe.scalalogging.LazyLogging

object HealthCheck extends LazyLogging {
  val name = "healthcheck"

  def run: Unit = {
    logger.info("Airflow containers and the batch jar are friends")
  }

  def main(args: Array[String]): Unit = {
    this.run
  }
}
