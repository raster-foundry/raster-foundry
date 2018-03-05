package com.azavea.rf.tile

import com.azavea.rf.common.Authentication
import com.azavea.rf.common.cache.CacheClient
import com.azavea.rf.common.cache.kryo.KryoMemcachedClient
import com.azavea.rf.database.{MapTokenDao, ProjectDao, UserDao}
import com.azavea.rf.datamodel.{User, Visibility}

import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directive1, Directives}
import doobie.util.transactor.Transactor
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.database.meta.RFMeta._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats.effect.IO
import scala.util.Try

import java.util.UUID

trait TileAuthentication extends Authentication
  with Directives {

  implicit def xa: Transactor[IO]

  // Default auth setting to true
  private val tileAuthSetting: String = sys.env.getOrElse("RF_TILE_AUTH_REQUIRED", "true")

  lazy val memcachedClient = KryoMemcachedClient.DEFAULT
  val rfCache = new CacheClient(memcachedClient)

  /** Check optional tile authentication
    *
    * Returns true if authenticated.
    * Performs authentication if set in environment variable or
    * if the environment variable is unset
    */
  def tileAuthenticateOption: Directive1[Boolean] = {

    val requireAuth = Try(tileAuthSetting.toBoolean).getOrElse(true)

    if (requireAuth) {
      authenticateWithParameter.flatMap {
        case _ => provide(true)
      }
    } else {
      provide(true)
    }
  }

  def isProjectPublic(id: UUID): Directive1[Boolean] =
    onSuccess(
      ProjectDao.query
        .filter(id)
        .filter(fr"visibility=${Visibility.Public.toString}")
        .selectOption.transact(xa).unsafeToFuture
    ).flatMap {
      case Some(_) => provide(true)
      case _ => provide(false)
    }

  def isProjectMapTokenValid(projectId: UUID): Directive1[Boolean] = {
    parameter('mapToken).flatMap { mapToken =>
      val mapTokenId = UUID.fromString(mapToken)

      val doesTokenExist = rfCache.caching(s"project-$projectId-token-$mapToken", 300) {
        MapTokenDao.query
          .filter(mapTokenId)
          .filter(fr"project_id=${projectId}")
          .selectOption
          .transact(xa).unsafeToFuture
      }

      onSuccess(doesTokenExist).flatMap {
        case Some(_) => provide(true)
        case _ => provide(false)
      }
    }
  }

  def authenticateToolTileRoutes(toolRunId: UUID): Directive1[User] = {
    parameters('mapToken.?, 'token.?).tflatMap {
      case (Some(mapToken), _) => validateMapTokenParameters(toolRunId, mapToken)
      case (_, Some(token)) => authenticateWithToken(token)
      case (_, _) => reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
    }
  }

  def validateMapTokenParameters(toolRunId: UUID, mapToken: String): Directive1[User] = {
    val mapTokenId = UUID.fromString(mapToken)
    val mapTokenQuery = rfCache.caching(s"mapToken-$mapTokenId-toolRunId-$toolRunId", 600) {
        MapTokenDao.query
          .filter(mapTokenId)
          .filter(fr"toolrun_id=${toolRunId}")
          .selectOption
          .transact(xa).unsafeToFuture
    }
    onSuccess(mapTokenQuery).flatMap {
      case Some(token) => {
        val userId = token.owner.toString
        val userFromId = rfCache.caching(s"user-$userId-token-$mapToken", 600) {
          UserDao.query.filter(fr"id=${userId}").selectOption.transact(xa).unsafeToFuture
        }
        onSuccess(userFromId).flatMap {
          case Some(user) => provide(user)
          case _ => reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
        }
      }
      case _ => reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
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
  def projectTileAccessAuthorized(projectId: UUID): Directive1[Boolean] =
    tileAuthenticateOption | isProjectMapTokenValid(projectId) | isProjectPublic(projectId)
}
