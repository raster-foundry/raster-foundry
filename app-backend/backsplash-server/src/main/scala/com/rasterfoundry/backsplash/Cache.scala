package com.rasterfoundry.backsplash.server

import com.rasterfoundry.datamodel.User
import com.typesafe.scalalogging.LazyLogging
import scalacache._
import scalacache.caffeine._
import scalacache.memoization._
import scalacache.CatsEffect.modes._

object Cache extends LazyLogging {

  val authorizationCacheFlags = Flags(Config.cache.authorizationCacheEnable,
                                      Config.cache.authenticationCacheEnable)
  val caffeineAuthorizationCache: Cache[Boolean] =
    CaffeineCache[Boolean]
  logger.info(
    s"Authorization Cache Status (read/write) ${authorizationCacheFlags}")

  val authenticationCacheFlags = Flags(Config.cache.authorizationCacheEnable,
                                       Config.cache.authenticationCacheEnable)
  val caffeineAuthenticationCache: Cache[Option[User]] =
    CaffeineCache[Option[User]]
  logger.info(s"Authentication Cache Status: ${authenticationCacheFlags}")

}
