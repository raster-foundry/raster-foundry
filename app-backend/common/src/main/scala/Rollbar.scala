package com.rasterfoundry.common

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

  def sendError(e: Throwable, traceId: String, path: String): Unit = {
    if (this.environment != "development") {
      rollbarClient.error(e, s"Exception thrown in request $path (traceId: $traceId)")
    } else {
      logger.error("What I would have sent to rollbar:", e)
    }
  }


  def sendError(e: Throwable): Unit = {
    if (this.environment != "development") {
      rollbarClient.error(e)
    } else {
      logger.error("What I would have sent to rollbar:", e)
    }
  }

  def sendError(s: String): Unit = {
    if (this.environment != "development") {
      rollbarClient.error(s)
    }
  }
}
