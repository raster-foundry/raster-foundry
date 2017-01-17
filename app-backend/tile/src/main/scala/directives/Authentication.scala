package com.azavea.rf.tile

import akka.http.scaladsl.server.{Directive1, Directives}
import com.azavea.rf.authentication.Authentication

import scala.util.Try


trait TileAuthentication extends Authentication with Directives {

  // Default auth setting to true
  private val tileAuthSetting: String = sys.env.getOrElse("RF_TILE_AUTH_REQUIRED", "true")

  /** Check optional tile authentication
    *
    * Returns true if authenticated.
    * Performs authentication if set in environment variable or
    * if the environment variable is unset
    */
  def tileAuthenticateOption: Directive1[Boolean] = {

    val requireAuth = Try(tileAuthSetting.toBoolean)
      .getOrElse(true)

    if (requireAuth) {
      validateTokenParameter.flatMap {
        case _ => provide(true)
      }
    } else {
      provide(true)
    }
  }
}