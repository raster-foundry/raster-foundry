package com.rasterfoundry.backsplash.server

import com.rasterfoundry.common.datamodel.User
import com.rasterfoundry.http4s.{Cache => Http4sUtilCache}

import com.typesafe.scalalogging.LazyLogging
import scalacache._
import scalacache.caffeine._

object Cache extends LazyLogging {

  val authorizationCacheFlags = Flags(Config.cache.authorizationCacheEnable,
                                      Config.cache.authorizationCacheEnable)
  val caffeineAuthorizationCache: Cache[Boolean] =
    CaffeineCache[Boolean]
  logger.info(
    s"Authorization Cache Status (read/write) ${authorizationCacheFlags}")

  val authenticationCacheFlags = Http4sUtilCache.authenticationCacheFlags
  val caffeineAuthenticationCache: Cache[Option[User]] =
    CaffeineCache[Option[User]]
  logger.info(
    s"Authentication Cache Status, backsplash: ${authenticationCacheFlags}")

}
