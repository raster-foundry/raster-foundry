package com.rasterfoundry.api.config

import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.database.FeatureFlagDao
import com.rasterfoundry.datamodel.FeatureFlag

import doobie.free.connection.ConnectionIO
import io.circe.generic.JsonCodec

@JsonCodec
final case class AngularConfig(
    clientId: String,
    clientEnvironment: String,
    auth0Domain: String,
    rollbarClientToken: String,
    intercomAppId: String,
    featureFlags: Seq[FeatureFlag],
    tileServerLocation: String,
    dropboxClientId: String
)

object AngularConfigService extends Config {
  def getConfig(): ConnectionIO[AngularConfig] =
    for {
      features <- FeatureFlagDao.query.list
    } yield
      AngularConfig(
        auth0ClientId,
        clientEnvironment,
        auth0Domain,
        rollbarClientToken,
        intercomAppId,
        features,
        tileServerLocation,
        dropboxClientId
      )
}
