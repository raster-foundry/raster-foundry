package com.rasterfoundry.http4s

import com.rasterfoundry.datamodel.UserWithPlatform

import com.typesafe.scalalogging.LazyLogging
import scalacache._
import scalacache.caffeine._

object Cache extends LazyLogging {

  val authenticationCacheFlags = Flags(
    Config.cache.authenticationCacheEnable,
    Config.cache.authenticationCacheEnable
  )
  val caffeineAuthenticationWithPlaformCache: Cache[Option[UserWithPlatform]] =
    CaffeineCache[Option[UserWithPlatform]]
}
