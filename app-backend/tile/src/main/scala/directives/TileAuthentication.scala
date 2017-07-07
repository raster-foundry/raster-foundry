package com.azavea.rf.tile

import java.util.UUID

import akka.http.scaladsl.server.{Directive1, Directives}
import com.azavea.rf.common.Authentication
import com.azavea.rf.database.ActionRunner
import com.azavea.rf.database.tables.{MapTokens, Projects}
import com.azavea.rf.datamodel.Visibility

import scala.util.Try


trait TileAuthentication extends Authentication
  with Directives
  with ActionRunner {

  // Default auth setting to true
  private val tileAuthSetting: String = sys.env.getOrElse("RF_TILE_AUTH_REQUIRED", "true")

  /** Check optional tile authentication
    *
    * Returns true if authenticated.
    * Performs authentication if set in environment variable or
    * if the environment variable is unset
    */
  def tileAuthenticateOption: Directive1[Boolean] = {

    val requireAuth = Try(tileAuthSetting.toBoolean).getOrElse(true)

    if (requireAuth) {
      validateTokenParameter.flatMap {
        case _ => provide(true)
      }
    } else {
      provide(true)
    }
  }

  def isProjectPublic(id: UUID): Directive1[Boolean] =
    onSuccess(Projects.getPublicProject(id)).flatMap {
      case Some(_) => provide(true)
      case _ => provide(false)
    }

  def isMapTokenValid(projectId: UUID): Directive1[Boolean] = {
    parameter('mapToken).flatMap { mapToken =>
      val mapTokenId = UUID.fromString(mapToken)
      onSuccess(readOneDirect(MapTokens.validateMapToken(projectId, mapTokenId))).flatMap {
        case 1 => provide(true)
        case _ => provide(false)
      }
    }
  }

  /** Authorize tile access if given valid token, mapToken, or if project is public
    *
    * isTokenParameterValid and isMapTokenValid only run if the respective
    * query parameter is specified. Since isTokenParameterValid does not make
    * a database call, it is the fastest and should be run first. If mapToken
    * is specified, we allow access if it is valid. We don't need to make an
    * additional database call to see if the project is public, since that would
    * not affect our decision. Finally, those accessing a public project would
    * not specify a token or mapToken parameter, so only the third directive
    * will run. We make a single database call to check that.
    *
    * This order guarantees that at most one database call is made in every case.
    */
  def tileAccessAuthorized(projectId: UUID): Directive1[Boolean] =
    isTokenParameterValid | isMapTokenValid(projectId) | isProjectPublic(projectId)
}
