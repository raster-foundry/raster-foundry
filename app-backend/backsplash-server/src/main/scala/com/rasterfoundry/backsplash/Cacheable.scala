package com.rasterfoundry.backsplash.server

import com.rasterfoundry.datamodel.Scene
import com.rasterfoundry.database.SceneDao

import cats.effect.IO
import doobie._
import doobie.implicits._
import scalacache._
import scalacache.CatsEffect.modes._
import scalacache.memoization._

import scala.concurrent.duration._

import java.util.UUID

object Cacheable {
  def getSceneById(ttl: Option[FiniteDuration])(
      sceneId: UUID,
      xa: Transactor[IO])(implicit cache: Cache[Scene]): IO[Scene] =
    memoizeF[IO, Scene](ttl) {
      SceneDao.unsafeGetSceneById(sceneId).transact(xa)
    }

  def getSceneById(sceneId: UUID, xa: Transactor[IO])(
      implicit cache: Cache[Scene]): IO[Scene] =
    getSceneById(Some(3 seconds))(sceneId, xa)

}
