package com.rasterfoundry.database

import com.typesafe.config.ConfigFactory

import java.util.UUID

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
    val advisoryLockConstant =
      statusReapingConfig.getInt("advisoryLockConstant")
  }

  object auth0Config {
    private val auth0Config = config.getConfig("auth0")
    val defaultPlatformId =
      UUID.fromString(auth0Config.getString("defaultPlatformId"))
    val defaultOrganizationId =
      UUID.fromString(auth0Config.getString("defaultOrganizationId"))
    val systemUser = auth0Config.getString("systemUser")
  }
}
