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
    val landsat45ThematicMapperDatasourceId =
      publicDataConfig.getString("landsat45TMDatasourceId")
    val landsat7ETMDatasourceId =
      publicDataConfig.getString("landsat7ETMDatasourceId")
    val enableMultiTiff =
      publicDataConfig.getBoolean("enableMultiTiff")
  }

  object sceneSearch {
    private val tmsConfig = config.getConfig("sceneSearch")
    val bufferPercentage = tmsConfig.getDouble("bufferPercentage")
  }

  object statusReapingConfig {
    private val statusReapingConfig = config.getConfig("statusReaping")
    val taskStatusExpirationSeconds =
      statusReapingConfig.getInt("taskStatusExpirationSeconds")
  }
}
