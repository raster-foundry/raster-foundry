package com.azavea.rf.api.grid

import scala.util.{Failure, Success}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import com.azavea.rf.common.utils.TileUtils
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe._
import geotrellis.vector.io.json._
import kamon.akka.http.KamonTraceDirectives
import com.azavea.rf.common.{Authentication, UserErrorHandler}
import com.azavea.rf.database.tables.Scenes
import com.azavea.rf.database.Database
import com.azavea.rf.database._

trait GridRoutes extends Authentication
    with GridQueryParameterDirective
    with UserErrorHandler
    with LazyLogging
    with KamonTraceDirectives {

  implicit def database: Database

  val gridRoutes: Route = handleExceptions(userExceptionHandler) {
    pathPrefix(IntNumber / IntNumber / IntNumber) { (z, x, y) =>
      get {
        traceName("scene-grid") {
          getGrid(z, x, y)
        }
      }
    }
  }

  def getGrid(z: Int, x: Int, y: Int): Route = authenticate { _ =>
    gridQueryParameters { gridParams =>
      complete {
        val tileBounds = TileUtils.TileCoordinates(z, x, y).childrenTileBounds
        Future.sequence(Scenes.sceneGrid(gridParams, tileBounds))
      }
    }
  }
}
