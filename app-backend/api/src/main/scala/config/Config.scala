package com.azavea.rf.api.config

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._
import com.azavea.rf.api.utils.Config
import com.azavea.rf.api.AkkaSystem
import com.azavea.rf.datamodel.FeatureFlag
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.FeatureFlags

case class AngularConfig(
  clientId: String,
  auth0Domain: String,
  rollbarClientToken: String,
  intercomAppId: String,
  featureFlags: Seq[FeatureFlag]
)

object AngularConfigService extends AkkaSystem.LoggerExecutor with Config {
  def getConfig()(implicit database: Database) = for {
    features:Seq[FeatureFlag] <- FeatureFlags.listFeatureFlags()
  } yield AngularConfig(auth0ClientId, auth0Domain, rollbarClientToken, intercomAppId, features)
}
