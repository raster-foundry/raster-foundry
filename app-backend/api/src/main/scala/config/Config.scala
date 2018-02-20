package com.azavea.rf.api.config

import cats.effect.IO
import com.azavea.rf.api.utils.Config
import com.azavea.rf.database.FeatureFlagDao
import com.azavea.rf.datamodel.FeatureFlag
import doobie.free.connection.ConnectionIO
import doobie.util.transactor.Transactor
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
  def getConfig(): ConnectionIO[AngularConfig] = for {
    features <- FeatureFlagDao.query.list
  } yield AngularConfig(
    auth0ClientId, clientEnvironment, auth0Domain, rollbarClientToken,
    intercomAppId, features, tileServerLocation, dropboxClientId
  )
}
