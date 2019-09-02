package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.error.NoDataInRegionException
import com.rasterfoundry.datamodel.{ProjectLayer, Scene}
import com.rasterfoundry.database.{ProjectLayerDao, SceneDao}
import com.rasterfoundry.database.Implicits._
import cats.effect.IO
import doobie._
import doobie.implicits._
import geotrellis.vector.{Polygon, Projected}
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
    memoizeF(Some(60 seconds)) {
      SceneDao.getSceneById(sceneId, window).transact(xa) flatMap {
        case Some(scene) => IO.pure { scene }
        case None        => IO.raiseError(NoDataInRegionException)
      }
    }

  def getProjectLayerById(projectLayerId: UUID,
                          @cacheKeyExclude xa: Transactor[IO])(
      implicit cache: Cache[ProjectLayer]): IO[ProjectLayer] =
    memoizeF(Some(60 seconds)) {
      ProjectLayerDao.getProjectLayerById(projectLayerId).transact(xa) flatMap {
        case Some(projectLayer) => IO.pure { projectLayer }
        // Not finding a project layer is like not finding scenes, in that
        // the user asked for data somewhere for a project layer, and we
        // definitely don't have it
        case None => IO.raiseError(NoDataInRegionException)
      }
    }
}
