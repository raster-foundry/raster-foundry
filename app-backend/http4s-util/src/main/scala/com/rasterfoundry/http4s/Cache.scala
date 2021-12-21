package com.rasterfoundry.http4s

import com.rasterfoundry.database.UserMaybePlatformId

import com.typesafe.scalalogging.LazyLogging
import scalacache._
import scalacache.caffeine._

object Cache extends LazyLogging {

  val authenticationCacheFlags = Flags(
    Config.cache.authenticationCacheEnable,
    Config.cache.authenticationCacheEnable
  )
  val caffeineAuthenticationCache: Cache[Option[UserMaybePlatformId]] =
    CaffeineCache[Option[UserMaybePlatformId]]
}
