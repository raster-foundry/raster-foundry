package com.azavea.rf.common

import com.rollbar.notifier.Rollbar
import com.rollbar.notifier.config.ConfigBuilder
import com.typesafe.scalalogging.LazyLogging

trait RollbarNotifier extends LazyLogging {

  val rollbarApiToken: String = sys.env.get("ROLLBAR_SERVER_TOKEN") match {
    case Some(t) => t
    case _ =>
      throw new RuntimeException(
        "Rollbar API token must be present to notify rollbar")
  }

  val environment: String = sys.env.get("ENVIRONMENT") match {
    case Some(env) => env
    case _         => "staging"
  }

  val rollbarClient: Rollbar = Rollbar.init(
    ConfigBuilder
      .withAccessToken(rollbarApiToken)
      .language("scala")
      .environment(environment)
      .build())

  def sendError(e: Throwable): Unit = {
    if (this.environment != "development")
      rollbarClient.error(e)
  }

  def sendError(s: String): Unit = {
    if (this.environment != "development")
      rollbarClient.error(s)
  }
}
