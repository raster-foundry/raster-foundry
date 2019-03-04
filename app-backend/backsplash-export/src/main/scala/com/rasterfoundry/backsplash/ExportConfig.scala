package com.rasterfoundry.backsplash

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

object ExportConfig extends LazyLogging {

  private val config = ConfigFactory.load()

  object Export {
    private val exportConfig = config.getConfig("export")
    val tileSize: Int = {
      val tileSize = exportConfig.getInt("tileSize")
      logger.debug(s"Using Tile Size: ${tileSize}")
      tileSize
    }

  }

  object RasterSource {
    private val rasterSourceConfig = config.getConfig("rasterSource")
    val enableGDAL: Boolean = rasterSourceConfig.getBoolean("enableGDAL")
  }

}
