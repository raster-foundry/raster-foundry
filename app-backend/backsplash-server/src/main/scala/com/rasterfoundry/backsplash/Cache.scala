package com.rasterfoundry.backsplash.server

import com.rasterfoundry.datamodel.{
  AuthResult,
  Project,
  ProjectLayer,
  Scene,
  ToolRun,
  User
}
import com.rasterfoundry.http4s.{Cache => Http4sUtilCache}

import com.typesafe.scalalogging.LazyLogging
import scalacache._
import scalacache.caffeine._
import com.rasterfoundry.datamodel.AnnotationProject

object Cache extends LazyLogging {

  val requestCounter = CaffeineCache[Int]

  val authorizationCacheFlags = Flags(
    Config.cache.authorizationCacheEnable,
    Config.cache.authorizationCacheEnable
  )
  val caffeineAuthorizationCache: Cache[Boolean] =
    CaffeineCache[Boolean]
  logger.info(
    s"Authorization Cache Status (read/write) ${authorizationCacheFlags}"
  )

  val caffeineSceneCache: Cache[Scene] =
    CaffeineCache[Scene]

  val caffeineProjectLayerCache: Cache[ProjectLayer] =
    CaffeineCache[ProjectLayer]

  val authenticationCacheFlags = Http4sUtilCache.authenticationCacheFlags
  val caffeineAuthenticationCache: Cache[Option[User]] =
    CaffeineCache[Option[User]]
  logger.info(
    s"Authentication Cache Status, backsplash: ${authenticationCacheFlags}"
  )

  val sceneAuthCache: Cache[AuthResult[Scene]] =
    CaffeineCache[AuthResult[Scene]]

  val projectAuthCache: Cache[AuthResult[Project]] =
    CaffeineCache[AuthResult[Project]]

  val annotationProjectAuthCache: Cache[AuthResult[AnnotationProject]] =
    CaffeineCache[AuthResult[AnnotationProject]]

  val toolRunAuthCache: Cache[AuthResult[ToolRun]] =
    CaffeineCache[AuthResult[ToolRun]]
}
