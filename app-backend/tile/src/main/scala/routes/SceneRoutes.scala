package com.azavea.rf.tile.routes

import com.azavea.rf.tile._
import com.azavea.rf.datamodel.ColorCorrect
import com.azavea.rf.datamodel.ColorCorrect.Params.colorCorrectParams
import com.azavea.rf.tile.tool._
import com.azavea.rf.tile.tool.ToolParams._

import geotrellis.raster._
import geotrellis.raster.io._
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.render.{Png, ColorRamp, ColorMap}
import geotrellis.spark._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._
import com.typesafe.scalalogging.LazyLogging
import cats.data._
import cats.implicits._
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

object SceneRoutes extends LazyLogging {

  def root: Route =
    pathPrefix(JavaUUID) { id =>
      pathPrefix("rgb") {
        layerTileAndHistogram(id) { (futureMaybeTile, futureHist) =>
          imageRoute(futureMaybeTile, futureHist)
        } ~
        pathPrefix("thumbnail") {
          pathEndOrSingleSlash {
            rejectEmptyResponse {
              get { imageThumbnailRoute(id) }
            }
          }
        } ~
        pathPrefix("histogram") {
          pathEndOrSingleSlash {
            rejectEmptyResponse {
              get { imageHistogramRoute(id) }
            }
          }
        }
      } ~
      rejectEmptyResponse {
        (pathPrefix("ndvi") & layerTile(id)){ futureMaybeTile =>
          get { toolRoute(futureMaybeTile, NDVI, Some(NDVI.colorRamp), Some(NDVI.breaks)) }
        } ~
        (pathPrefix("ndwi") & layerTile(id)){ futureMaybeTile =>
          get { toolRoute(futureMaybeTile, NDWI, Some(NDWI.colorRamp), Some(NDWI.breaks)) }
        } ~
        (pathPrefix("grey") & layerTile(id)){ futureMaybeTile =>
          get { toolRoute(futureMaybeTile, _.band(0), Some(ColorRamp(Array(0x000000, 0xFFFFFF)))) }
        }
      }
    }

  def layerTile(layer: UUID) =
    pathPrefix(IntNumber / IntNumber / IntNumber).tmap[Future[Option[MultibandTile]]] {
      case (zoom: Int, x: Int, y: Int) =>
        LayerCache.layerTile(layer, zoom, SpatialKey(x, y)).value
    }

  def layerTileAndHistogram(id: UUID) =
    pathPrefix(IntNumber / IntNumber / IntNumber).tmap[(OptionT[Future, MultibandTile], OptionT[Future, Array[Histogram[Double]]])] {
      case (zoom: Int, x: Int, y: Int) =>
        val futureMaybeTile = LayerCache.layerTile(id, zoom, SpatialKey(x, y))
        val futureHistogram = LayerCache.layerHistogram(id, zoom)
        (futureMaybeTile, futureHistogram)
    }

  def pngAsHttpResponse(png: Png): HttpResponse =
    HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), png.bytes))

  def imageThumbnailRoute(id: UUID) =
    colorCorrectParams { params =>
      parameters('size.as[Int].?(256)) { size =>
      complete {
        val futureHist = LayerCache.layerHistogram(id, 0)
        val futureMaybeTile = StitchLayer(id, size)
        val futureResponse = 
          for {
            tile <- futureMaybeTile
            hist <- futureHist
          } yield {
            val (rgbTile, rgbHist) = params.reorderBands(tile, hist)
            pngAsHttpResponse(ColorCorrect(rgbTile, rgbHist, params).renderPng)
          }
        futureResponse.value
      }
    }
  }

  def imageHistogramRoute(id: UUID) =
    colorCorrectParams { params =>
      parameters('size.as[Int].?(64)) { size =>
      import DefaultJsonProtocol._
      complete {
        val futureMaybeTile = StitchLayer(id, size)
        val futureHist = LayerCache.layerHistogram(id, 0)
        val futureResponse =
          for {
            tile <- futureMaybeTile
            hist <- futureHist
          } yield {
            val (rgbTile, rgbHist) = params.reorderBands(tile, hist)
            ColorCorrect(rgbTile, rgbHist, params).bands.map(_.histogram).toArray
          }
        futureResponse.value
      }
    }
  }

  def imageRoute(futureMaybeTile: OptionT[Future, MultibandTile], futureHist: OptionT[Future, Array[Histogram[Double]]]): Route =
    colorCorrectParams { params =>
      complete {
        val futureResponse =
          for {
            tile <- futureMaybeTile
            hist <- futureHist
          } yield {
            val (rgbTile, rgbHist) = params.reorderBands(tile, hist)
            pngAsHttpResponse(ColorCorrect(rgbTile, rgbHist, params).renderPng)
          }
        futureResponse.value
      }
    }

  def toolRoute(
    futureMaybeTile: Future[Option[MultibandTile]],
    index: MultibandTile => Tile,
    defaultColorRamp: Option[ColorRamp] = None,
    defaultBreaks: Option[Array[Double]] = None
  ): Route = {
    toolParams(defaultColorRamp, defaultBreaks) { params =>
      complete {
        for {
          maybeTile <- futureMaybeTile
        } yield {
          maybeTile.map { tile =>
            val subsetTile = tile.subsetBands(params.bands)
            val colorMap = ColorMap(params.breaks, params.ramp)
            pngAsHttpResponse(index(subsetTile).renderPng(colorMap))
          }
        }
      }
    }
  }
}

