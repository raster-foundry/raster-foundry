package com.azavea.rf.grid

import scala.util.{Success, Failure}
import scala.concurrent.ExecutionContext.Implicits.global

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import geotrellis.vector.io.json._

import com.azavea.rf.auth.Authentication
import com.azavea.rf.database.tables.Scenes
import com.azavea.rf.database.Database
import com.azavea.rf.database._
import com.azavea.rf.utils.{UserErrorHandler, Aggregation}
import com.typesafe.scalalogging.LazyLogging

trait GridRoutes extends Authentication
    with GridQueryParameterDirective
    with UserErrorHandler
    with LazyLogging {
  implicit def database: Database

  val gridRoutes: Route = handleExceptions(userExceptionHandler) {
    pathPrefix(Segment / IntNumber) { (bbox, zoom) =>
      get { getSceneGrid(bbox, zoom.toInt) }
    }
  }

  def getSceneGrid(bbox: String, zoom: Int): Route = authenticate { user =>
    (gridQueryParameters) { (gridParams) =>
      complete {
        Scenes.aggregateScenes(
          gridParams,
          Aggregation.latLngBboxFromString(bbox),
          Aggregation.bboxToGrid(bbox, zoom)
        ).map( aggregation =>
          Aggregation.constructGeojson(aggregation)
        )
      }
    }
  }
}
