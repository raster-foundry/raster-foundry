package com.azavea.rf.batch.stac

import com.typesafe.scalalogging.LazyLogging

object ReadStacFeature extends LazyLogging {
  val name = "read_stac_feature"
  def main(args: Array[String]): Unit = {
    val allArgs = args.mkString(" ")
    logger.info(s"Read Stac Feature operation called with args: ${allArgs}")
  }
}
