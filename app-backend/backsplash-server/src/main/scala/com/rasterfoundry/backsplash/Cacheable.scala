package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.error.NoDataInRegionException
import com.rasterfoundry.backsplash.error.NoDataInRegionException
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.{ProjectLayerDao, SceneDao}
import com.rasterfoundry.datamodel.{ProjectLayer, Scene}

import cats.effect.IO
import com.colisweb.tracing.TracingContext
import doobie._
import doobie.implicits._
import geotrellis.vector.{Polygon, Projected}
import scalacache.CatsEffect.modes._
import scalacache._
import scalacache.memoization._

import scala.concurrent.duration._

import java.util.UUID

object Cacheable {
  def getSceneById(sceneId: UUID,
                   window: Option[Projected[Polygon]],
                   @cacheKeyExclude xa: Transactor[IO],
                   @cacheKeyExclude tracingContext: TracingContext[IO])(
      implicit cache: Cache[Scene]): IO[Scene] = {
    val tags = Map("sceneId" -> sceneId.toString)
    tracingContext.childSpan("cache.getSceneById", tags) use { context =>
      memoizeF(Some(60 seconds)) {
        context.childSpan("db.getSceneById", tags) use (_ =>
          SceneDao.getSceneById(sceneId, window).transact(xa)) flatMap {
          case Some(scene) =>
            IO.pure {
              scene
            }
          case None => IO.raiseError(NoDataInRegionException)
        }
      }
    }
  }

  def getProjectLayerById(projectLayerId: UUID,
                          @cacheKeyExclude xa: Transactor[IO],
                          @cacheKeyExclude tracingContext: TracingContext[IO])(
      implicit cache: Cache[ProjectLayer]): IO[ProjectLayer] = {
    val tags = Map("projectLayerId" -> projectLayerId.toString)
    tracingContext.childSpan("cache.getProjectLayerById", tags) use { context =>
      memoizeF(Some(60 seconds)) {
        context.childSpan("db.getProjectLayerById", tags) use { _ =>
          ProjectLayerDao.getProjectLayerById(projectLayerId).transact(xa)
        } flatMap {
          case Some(projectLayer) =>
            IO.pure {
              projectLayer
            }
          // Not finding a project layer is like not finding scenes, in that
          // the user asked for data somewhere for a project layer, and we
          // definitely don't have it
          case None => IO.raiseError(NoDataInRegionException)
        }
      }
    }
  }
}
