package com.rasterfoundry.http4s

import com.rasterfoundry.datamodel.User
import com.typesafe.scalalogging.LazyLogging
import scalacache._
import scalacache.caffeine._
import scalacache.memoization._
import scalacache.CatsEffect.modes._

object Cache extends LazyLogging {

  val authenticationCacheFlags = Flags(Config.cache.authenticationCacheEnable)
  val caffeineAuthenticationCache: Cache[Option[User]] =
    CaffeineCache[Option[User]]
  logger.info(s"Authentication Cache Status: ${authenticationCacheFlags}")

}
