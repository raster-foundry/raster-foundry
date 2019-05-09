package com.rasterfoundry.http4s

import com.rasterfoundry.datamodel.User

import com.typesafe.scalalogging.LazyLogging
import scalacache._
import scalacache.caffeine._

object Cache extends LazyLogging {

  val authenticationCacheFlags = Flags(Config.cache.authenticationCacheEnable,
                                       Config.cache.authenticationCacheEnable)
  val caffeineAuthenticationCache: Cache[Option[User]] =
    CaffeineCache[Option[User]]
}
