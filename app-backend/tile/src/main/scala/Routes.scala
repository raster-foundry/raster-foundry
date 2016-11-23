package com.azavea.rf.tile

import com.azavea.rf.tile.image._
import com.azavea.rf.tile.image.ColorCorrectParams._
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
import com.typesafe.scalalogging.LazyLogging
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import java.util.UUID

trait Routes extends LazyLogging {

  def layerTile(layer: RfLayerId) =
    pathPrefix(IntNumber / IntNumber / IntNumber).tmap[Future[Option[MultibandTile]]] {
      case (zoom: Int, x: Int, y: Int) =>
        LayerCache.maybeTile(layer, zoom, SpatialKey(x, y))
    }

  def layerTileAndHistogram(id: RfLayerId) =
    pathPrefix(IntNumber / IntNumber / IntNumber).tmap[(Future[Option[MultibandTile]], Future[Array[Histogram[Double]]])] {
      case (zoom: Int, x: Int, y: Int) =>
        val futureMaybeTile = LayerCache.maybeTile(id, zoom, SpatialKey(x, y))
        val futureHistogram = LayerCache.bandHistogram(id, zoom)
        (futureMaybeTile, futureHistogram)
    }

  def pngAsHttpResponse(png: Png): HttpResponse =
    HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), png.bytes))

  def singleLayer: Route =
    pathPrefix(JavaUUID / Segment / JavaUUID).as(RfLayerId) { id =>
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

  def imageThumbnailRoute(id: RfLayerId) =
    colorCorrectParams { params =>
      parameters('size.as[Int].?(256)) { size =>
      complete {
        val futureHist = LayerCache.bandHistogram(id, 0)
        val futureMaybeTile = StitchLayer(id, size)

        for {
          maybeTile <- futureMaybeTile
          layerHist <- futureHist
        } yield {
          maybeTile.map { tile =>
            val (rgbTile, rgbHist) = params.reorderBands(tile, layerHist)
            pngAsHttpResponse(ColorCorrect(rgbTile, rgbHist, params).renderPng)
          }
        }
      }
    }
  }

  def imageHistogramRoute(id: RfLayerId) =
    colorCorrectParams { params =>
      parameters('size.as[Int].?(64)) { size =>
      import DefaultJsonProtocol._
      complete {
        val futureMaybeTile = StitchLayer(id, size)
        val futureHist = LayerCache.bandHistogram(id, 0)
        for {
          maybeTile <- futureMaybeTile
          layerHist <- futureHist
        } yield {
          maybeTile.map { tile =>
            val (rgbTile, rgbHist) = params.reorderBands(tile, layerHist)
            ColorCorrect(rgbTile, rgbHist, params).bands.map(_.histogram).toArray
          }
        }
      }
    }
  }

  def imageRoute(futureMaybeTile: Future[Option[MultibandTile]], futureHist: Future[Array[Histogram[Double]]]): Route =
    colorCorrectParams { params =>
      complete {
        for {
          maybeTile <- futureMaybeTile
          layerHist <- futureHist
        } yield {
          maybeTile.map { tile =>
            val (rgbTile, rgbHist) = params.reorderBands(tile, layerHist)
            pngAsHttpResponse(ColorCorrect(rgbTile, rgbHist, params).renderPng)
          }
        }
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

  def mosaicLayer: Route =
    pathPrefix(JavaUUID / Segment / "mosaic" / IntNumber / IntNumber / IntNumber) { (orgId, userId, zoom, x, y) =>
      colorCorrectParams { params =>
        parameters('scene.*) { scenes =>
          get {
            complete {
              val ids = scenes.map(id => RfLayerId(orgId, userId, UUID.fromString(id)))
              Mosaic(params, ids, zoom, x, y).map { maybeTile =>
                maybeTile.map { tile => pngAsHttpResponse(tile.renderPng())}
              }
            }
          }
        }
      }
    }
}

object Routes extends Routes
