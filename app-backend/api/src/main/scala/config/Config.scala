package com.azavea.rf.api.config

import com.azavea.rf.api.utils.Config
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.FeatureFlags
import com.azavea.rf.datamodel.FeatureFlag
import io.circe.generic.JsonCodec

import scala.concurrent.ExecutionContext.Implicits.global

@JsonCodec
case class AngularConfig(
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
  def getConfig()(implicit database: Database) = for {
    features: Seq[FeatureFlag] <- FeatureFlags.listFeatureFlags()
  } yield AngularConfig(
    auth0ClientId, clientEnvironment, auth0Domain, rollbarClientToken,
    intercomAppId, features, tileServerLocation, dropboxClientId
  )
}
