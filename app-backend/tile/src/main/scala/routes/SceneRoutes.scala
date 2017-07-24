package com.azavea.rf.tile.routes

import com.azavea.rf.common.RfStackTrace
import com.azavea.rf.tile._
import com.azavea.rf.tile.tool._
import com.azavea.rf.tile.tool.ToolParams._

import geotrellis.raster._
import geotrellis.raster.io._
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.render.{ColorMap, ColorRamp, Png}

import geotrellis.spark._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.dispatch.MessageDispatcher
import spray.json._
import com.typesafe.scalalogging.LazyLogging
import cats.data._
import cats.implicits._

import java.util.UUID
import scala.concurrent._
import scala.util._

object SceneRoutes extends LazyLogging with KamonTraceDirectives {

  def root(implicit dispatcher: MessageDispatcher): Route =
    pathPrefix(JavaUUID) { id =>
      pathPrefix("rgb") {
        traceName("rgb") {
          layerTileAndHistogram(id)(implicitly[ExecutionContext]) { (futureMaybeTile, _) =>
            imageRoute(futureMaybeTile)
          }
        } ~
        pathPrefix("thumbnail") {
          traceName("thumbnail") {
            pathEndOrSingleSlash {
              rejectEmptyResponse {
                get { imageThumbnailRoute(id) }
              }
            }
          }
        } ~
        pathPrefix("histogram") {
          traceName("histogram") {
            pathEndOrSingleSlash {
              rejectEmptyResponse {
                get { imageHistogramRoute(id) }
              }
            }
          }
        }
      } ~
      rejectEmptyResponse {
        (pathPrefix("ndvi") & layerTile(id)){ futureMaybeTile =>
          traceName("ndvi") { get { toolRoute(futureMaybeTile, NDVI, Some(NDVI.colorRamp), Some(NDVI.breaks)) } }
        } ~
        (pathPrefix("ndwi") & layerTile(id)){ futureMaybeTile =>
          traceName("ndwi") { get { toolRoute(futureMaybeTile, NDWI, Some(NDWI.colorRamp), Some(NDWI.breaks)) } }
        } ~
        (pathPrefix("grey") & layerTile(id)){ futureMaybeTile =>
          traceName("grey") { get { toolRoute(futureMaybeTile, _.band(0), Some(ColorRamp(Array(0x000000, 0xFFFFFF)))) } }
        }
      }
    }

  def layerTile(layer: UUID)(implicit ec: ExecutionContext) =
    pathPrefix(IntNumber / IntNumber / IntNumber).tmap[Future[Option[MultibandTile]]] {
      case (zoom: Int, x: Int, y: Int) =>
        LayerCache.layerTile(layer, zoom, SpatialKey(x, y)).value
    }

  def layerTileAndHistogram(id: UUID)(implicit ec: ExecutionContext) =
    pathPrefix(IntNumber / IntNumber / IntNumber).tmap[(OptionT[Future, MultibandTile], OptionT[Future, Array[Histogram[Double]]])] {
      case (zoom: Int, x: Int, y: Int) =>
        val futureMaybeTile = LayerCache.layerTile(id, zoom, SpatialKey(x, y))
        val futureHistogram = LayerCache.layerHistogram(id, zoom)
        (futureMaybeTile, futureHistogram)
    }

  def pngAsHttpResponse(png: Png): HttpResponse =
    HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), png.bytes))

  def imageThumbnailRoute(id: UUID)(implicit dispatcher: MessageDispatcher) =
    parameters('size.as[Int].?(256)) { size =>
      complete {
        val futureMaybeTile = StitchLayer(id, size)
        val futureResponse =
          for {
            tile <- futureMaybeTile
          } yield {
            pngAsHttpResponse(tile.renderPng)
          }
        val future = futureResponse.value

        future onComplete {
          case Success(s) => s
          case Failure(e) =>
            logger.error(s"Message: ${e.getMessage}\nStack trace: ${RfStackTrace(e)}")
        }

        future
      }
    }

  def imageHistogramRoute(id: UUID)(implicit dispatcher: MessageDispatcher) = {
    import DefaultJsonProtocol._
    complete {
      val futureResponse =
        for {
          hist <- LayerCache.layerHistogram(id, 0).value
        } yield {
          hist.toArray
        }

      futureResponse onComplete {
        case Success(s) => s
        case Failure(e) =>
          logger.error(s"Message: ${e.getMessage}\nStack trace: ${RfStackTrace(e)}")
      }

      futureResponse.value
    }
  }

  def imageRoute(futureMaybeTile: OptionT[Future, MultibandTile])(implicit dispatcher: MessageDispatcher): Route =
    complete {
      val futureResponse =
        for {
          tile <- futureMaybeTile
        } yield {
          pngAsHttpResponse(tile.renderPng)
        }
      val future = futureResponse.value

      future onComplete {
        case Success(s) => s
        case Failure(e) =>
          logger.error(s"Message: ${e.getMessage}\nStack trace: ${RfStackTrace(e)}")
      }

      future
    }

  def toolRoute(
    futureMaybeTile: Future[Option[MultibandTile]],
    index: MultibandTile => Tile,
    defaultColorRamp: Option[ColorRamp] = None,
    defaultBreaks: Option[Array[Double]] = None
  )(implicit dispatcher: MessageDispatcher): Route = {
    toolParams(defaultColorRamp, defaultBreaks) { params =>
      complete {
        val future =
          for {
            maybeTile <- futureMaybeTile
          } yield {
            maybeTile.map { tile =>
              val subsetTile = tile.subsetBands(params.bands)
              val colorMap = ColorMap(params.breaks, params.ramp)
              pngAsHttpResponse(index(subsetTile).renderPng(colorMap))
            }
          }

        future onComplete {
          case Success(s) => s
          case Failure(e) =>
            logger.error(s"Message: ${e.getMessage}\nStack trace: ${RfStackTrace(e)}")
        }

        future
      }
    }
  }
}

