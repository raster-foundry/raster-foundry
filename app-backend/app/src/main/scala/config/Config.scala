package com.azavea.rf.config

import scala.concurrent.{Future, ExecutionContext}
import com.azavea.rf.utils.Config
import com.azavea.rf.database.Database
import com.azavea.rf.AkkaSystem

case class AngularConfig(clientId: String)

object AngularConfigService extends AkkaSystem.LoggerExecutor with Config {
  def getConfig()(implicit database: Database, ec: ExecutionContext):
      AngularConfig = {
    return AngularConfig(auth0ClientId)
  }
}
