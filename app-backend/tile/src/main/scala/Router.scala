package com.azavea.rf.tile

import com.azavea.rf.database.Database
import com.azavea.rf.tile.image._
import com.azavea.rf.tile.tool._
import com.azavea.rf.tile.tool.ToolParams._
import com.azavea.rf.datamodel.ColorCorrect
import ColorCorrect.Params.colorCorrectParams
import com.azavea.rf.tile.routes._

import geotrellis.raster._
import geotrellis.raster.io._
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.render.{Png, ColorRamp, ColorMap}
import geotrellis.spark._
import geotrellis.spark.io._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes, StatusCodes}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.typesafe.scalalogging.LazyLogging
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

class Router(db: Database) extends LazyLogging {
  def exceptionHandler =
    ExceptionHandler {
      case e: ValueNotFoundError =>
        complete(StatusCodes.NotFound)
      case e: IllegalArgumentException =>
        complete(StatusCodes.ClientError(400)("Bad Argument", e.getMessage))
      case e: IllegalStateException =>
        complete(StatusCodes.ClientError(400)("Bad Request", e.getMessage))
    }

  def root = handleExceptions(exceptionHandler) {
    pathPrefix("tiles") {
      pathPrefix("healthcheck") {
        pathEndOrSingleSlash {
          get {
            complete {
              HttpResponse(StatusCodes.OK)
            }
          }
        }
      } ~
      SceneRoutes.root ~
      MosaicRoutes.mosaicProject(db) ~
      pathPrefix("tools") {
        ToolRoutes.root(db)
      }
    }
  }
}
