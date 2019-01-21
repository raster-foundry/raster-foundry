package com.rasterfoundry.backsplash.server

import com.rasterfoundry.datamodel.User
import com.rasterfoundry.http4s.{Cache => Http4sUtilCache}
import com.typesafe.scalalogging.LazyLogging
import scalacache._
import scalacache.caffeine._
import scalacache.memoization._
import scalacache.CatsEffect.modes._

object Cache extends LazyLogging {

  val authorizationCacheFlags = Http4sUtilCache.authenticationCacheFlags
  val caffeineAuthorizationCache: Cache[Boolean] =
    CaffeineCache[Boolean]
  logger.info(
    s"Authorization Cache Status (read/write) ${authorizationCacheFlags}")

  val authenticationCacheFlags = Flags(Config.cache.authenticationCacheEnable,
                                       Config.cache.authenticationCacheEnable)
  val caffeineAuthenticationCache: Cache[Option[User]] =
    CaffeineCache[Option[User]]
  logger.info(
    s"Authentication Cache Status, backsplash: ${authenticationCacheFlags}")

}
