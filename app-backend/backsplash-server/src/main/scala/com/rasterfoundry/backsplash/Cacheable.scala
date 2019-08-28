package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.error.NoDataInRegionException
import com.rasterfoundry.datamodel.Scene
import com.rasterfoundry.database.SceneDao
import com.rasterfoundry.database.Implicits._

import cats.effect.IO
import doobie._
import doobie.implicits._
import geotrellis.vector.{Projected, Polygon}
import scalacache._
import scalacache.CatsEffect.modes._
import scalacache.memoization._

import scala.concurrent.duration._

import java.util.UUID
import com.rasterfoundry.backsplash.error.NoDataInRegionException

object Cacheable {
  def getSceneById(sceneId: UUID,
                   window: Option[Projected[Polygon]],
                   @cacheKeyExclude xa: Transactor[IO])(
      implicit cache: Cache[Scene]): IO[Scene] =
    memoizeF(Some(3 seconds)) {
      SceneDao.getSceneById(sceneId, window).transact(xa) flatMap {
        case Some(scene) => IO.pure { scene }
        case None        => IO.raiseError(NoDataInRegionException)
      }
    }
}
