package com.azavea.rf.tile

import com.azavea.rf.tile.image._
import com.azavea.rf.tile.image.ColorCorrectParams._
import com.azavea.rf.tile.model._
import com.azavea.rf.tile.model.ModelParams._

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

trait Routes extends LazyLogging {
  def layerTile(layer: RfLayerId) =
    pathPrefix(IntNumber / IntNumber / IntNumber).tmap[Future[MultibandTile]] {
      case (zoom: Int, x: Int, y: Int) =>
        LayerCache.tile(layer, zoom, SpatialKey(x, y))
    }

  def layerTileAndHistogram(id: RfLayerId) =
    pathPrefix(IntNumber / IntNumber / IntNumber).tmap[(Future[MultibandTile], Future[Array[Histogram[Double]]])] {
      case (zoom: Int, x: Int, y: Int) =>
        val futureTile = LayerCache.tile(id, zoom, SpatialKey(x, y))
        val futureHistogram = LayerCache.bandHistogram(id, zoom)
        (futureTile, futureHistogram)
    }

  def pngAsHttpResponse(png: Png): HttpResponse =
    HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), png.bytes))

  def singleLayer: Route =
    pathPrefix(JavaUUID / Segment / JavaUUID).as(RfLayerId) { id =>
      pathPrefix("rgb") {
        layerTileAndHistogram(id) { (futureTile, futureHist) =>
          imageRoute(futureTile, futureHist)
        } ~
        pathPrefix("thumbnail") {
          pathEndOrSingleSlash {
            get { imageThumbnailRoute(id) }
          }
        } ~
        pathPrefix("histogram") {
          pathEndOrSingleSlash {
            get { imageHistogramRoute(id) }
          }
        }
      } ~
      (pathPrefix("ndvi") & layerTile(id)){ futureTile =>
        get { modelRoute(futureTile, NDVI, Some(NDVI.colorRamp), Some(NDVI.breaks)) }
      } ~
      (pathPrefix("ndwi") & layerTile(id)){ futureTile =>
        get { modelRoute(futureTile, NDWI, Some(NDWI.colorRamp), Some(NDWI.breaks)) }
      } ~
      (pathPrefix("grey") & layerTile(id)){ futureTile =>
        get { modelRoute(futureTile, _.band(0), Some(ColorRamp(Array(0x000000, 0xFFFFFF)))) }
      }
    }

  def imageThumbnailRoute(id: RfLayerId) =
    colorCorrectParams { params =>
      parameters('size.as[Int].?(256)) { size =>
      complete {
        val futureHist = LayerCache.bandHistogram(id, 0)
        val futureTile = StitchLayer(id, size)

        for {
          tile <- futureTile
          layerHist <- futureHist
        } yield {
          val (rgbTile, rgbHist) = params.reorderBands(tile, layerHist)
          pngAsHttpResponse(ColorCorrect(rgbTile, rgbHist, params).renderPng)
        }
      }
    }
  }

  def imageHistogramRoute(id: RfLayerId) =
    colorCorrectParams { params =>
      parameters('size.as[Int].?(64)) { size =>
      import DefaultJsonProtocol._
      complete {
        val futureTile = StitchLayer(id, size)
        val futureHist = LayerCache.bandHistogram(id, 0)
        for {
          tile <- futureTile
          layerHist <- futureHist
        } yield {
          val (rgbTile, rgbHist) = params.reorderBands(tile, layerHist)
          ColorCorrect(rgbTile, rgbHist, params).bands.map(_.histogram).toArray
        }
      }
    }
  }

  def imageRoute(futureTile: Future[MultibandTile], futureHist: Future[Array[Histogram[Double]]]): Route =
    colorCorrectParams { params =>
      complete {
        for {
          tile <- futureTile
          layerHist <- futureHist
        } yield {
          val (rgbTile, rgbHist) = params.reorderBands(tile, layerHist)
          pngAsHttpResponse(ColorCorrect(rgbTile, rgbHist, params).renderPng)
        }
      }
    }

  def modelRoute(
    futureTile: Future[MultibandTile],
    index: MultibandTile => Tile,
    defaultColorRamp: Option[ColorRamp] = None,
    defaultBreaks: Option[Array[Double]] = None
  ): Route = {
    modelParams(defaultColorRamp, defaultBreaks) { params =>
        complete {
          for {
            tile <- futureTile
          } yield {
            val subsetTile = tile.subsetBands(params.bands)
            val colorMap = ColorMap(params.breaks, params.ramp)
            pngAsHttpResponse(index(subsetTile).renderPng(colorMap))
          }
        }
      }
    }
}

object Routes extends Routes
