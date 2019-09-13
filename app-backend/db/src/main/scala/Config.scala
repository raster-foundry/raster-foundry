package com.rasterfoundry.database

import com.typesafe.config.ConfigFactory

object Config {
  private val config = ConfigFactory.load()

  object publicData {
    private val publicDataConfig = config.getConfig("publicData")
    val landsat8DatasourceId =
      publicDataConfig.getString("landsat8DatasourceId")
    val sentinel2DatasourceId =
      publicDataConfig.getString("sentinel2DatasourceId")
  }
}
