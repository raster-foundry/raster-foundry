package com.rasterfoundry.api.utils

import com.rasterfoundry.notification.intercom.Model._

import com.typesafe.config.ConfigFactory

trait Config {
  val config = ConfigFactory.load()
  private val httpConfig = config.getConfig("http")
  private val auth0Config = config.getConfig("auth0")
  private val clientConfig = config.getConfig("client")
  private val intercomConfig = config.getConfig("intercom")
  private val rollbarConfig = config.getConfig("rollbar")
  private val s3Config = config.getConfig("s3")
  private val tileServerConfig = config.getConfig("tileServer")
  private val dropboxConfig = config.getConfig("dropbox")
  private val sentinel2Config = config.getConfig("sentinel2")
  private val groundworkConfig = config.getConfig("groundwork")

  val httpHost = httpConfig.getString("interface")
  val httpPort = httpConfig.getInt("port")

  val auth0Domain = auth0Config.getString("domain")
  val auth0Bearer = auth0Config.getString("bearer")
  val auth0ClientId = auth0Config.getString("clientId")
  val auth0ManagementClientId = auth0Config.getString("managementClientId")
  val auth0ManagementSecret = auth0Config.getString("managementSecret")
  val auth0GroundworkConnectionName =
    auth0Config.getString("groundworkConnectionName")
  val auth0AnonymizedConnectionName =
    auth0Config.getString("anonymizedUserCreateConnectionName")
  val auth0AnonymizedConnectionId =
    auth0Config.getString("anonymizedUserCreateConnectionId")
  val auth0AnonymizedConnectionAltName =
    auth0Config.getString("anonymizedUserCreateConnectionAltName")
  val auth0AnonymizedConnectionAltId =
    auth0Config.getString("anonymizedUserCreateConnectionAltId")

  val clientEnvironment = clientConfig.getString("clientEnvironment")

  val intercomAppId = intercomConfig.getString("appId")
  val intercomToken = IntercomToken(intercomConfig.getString("token"))
  val intercomAdminId = AdminId(intercomConfig.getString("adminId"))
  val groundworkUrlBase = intercomConfig.getString("groundworkUrlBase")

  val rollbarClientToken = rollbarConfig.getString("clientToken")

  val region = s3Config.getString("region")
  val dataBucket = s3Config.getString("dataBucket")
  val thumbnailBucket = s3Config.getString("thumbnailBucket")

  val tileServerLocation = tileServerConfig.getString("location")

  val dropboxClientId = dropboxConfig.getString("appKey")

  val scopedUploadRoleArn = s3Config.getString("scopedUploadRoleArn")

  val sentinel2DatasourceId = sentinel2Config.getString("datasourceId")

  val groundworkSampleProject = groundworkConfig.getString("sampleProject")
}
