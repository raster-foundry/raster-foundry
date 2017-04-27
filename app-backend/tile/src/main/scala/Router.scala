package com.azavea.rf.tile

import com.azavea.rf.database.Database
import com.azavea.rf.tile.image._
import com.azavea.rf.tile.tool._
import com.azavea.rf.tile.tool.ToolParams._
import com.azavea.rf.tile.routes._
import com.azavea.rf.tile.tool.TileSources
import com.azavea.rf.common.UserErrorHandler
import com.azavea.rf.datamodel.ColorCorrect
import com.azavea.rf.datamodel.ColorCorrect.Params.colorCorrectParams


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

class Router extends LazyLogging
    with TileAuthentication
    with TileErrorHandler {

  implicit lazy val database = Database.DEFAULT
  implicit val system = AkkaSystem.system
  implicit val materializer = AkkaSystem.materializer

  val toolRoutes = new ToolRoutes()

  def root = handleExceptions(tileExceptionHandler) {
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
      tileAuthenticateOption { _ =>
        SceneRoutes.root ~
        pathPrefix("tools") {
          toolRoutes.root(TileSources.cachedTmsSource)
        }
      } ~
      pathPrefix(JavaUUID) { projectId =>
        tileAccessAuthorized(projectId) {
          case true => MosaicRoutes.mosaicProject(projectId)(database)
          case _ => reject(AuthorizationFailedRejection)
        }
      }
    }
  }
}
