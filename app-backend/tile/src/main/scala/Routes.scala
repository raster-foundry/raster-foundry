package com.azavea.rf.tile

import java.util.UUID

import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import com.azavea.rf.tile.image._
import com.azavea.rf.tile.image.ImageParams._
import com.azavea.rf.tile.model._
import com.azavea.rf.tile.model.ModelParams._
import geotrellis.raster._
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.render.{ColorRamp, ColorMap}
import geotrellis.spark._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._


trait Routes extends LazyLogging {
  def layerTile(prefix: String, layerId: UUID) =
    pathPrefix(IntNumber / IntNumber / IntNumber).tmap[Future[MultibandTile]] {
      case (zoom: Int, x: Int, y: Int) =>
        LayerCache.tile(prefix, layerId, zoom, SpatialKey(x, y))
    }

  def layerTileAndHistogram(prefix: String, layerId: UUID) =
    pathPrefix(IntNumber / IntNumber / IntNumber).tmap[(Future[MultibandTile], Future[Array[Histogram[Double]]])] {
      case (zoom: Int, x: Int, y: Int) =>
        val futureTile = LayerCache.tile(prefix, layerId, zoom, SpatialKey(x, y))
        val futureHistogram = LayerCache.bandHistogram(prefix, layerId, zoom)
        (futureTile, futureHistogram)
    }

  def singleLayer: Route =
    pathPrefix(Segment / JavaUUID) { (prefix, layerId) =>
      (pathPrefix("rgb") & layerTileAndHistogram(prefix, layerId)){ (futureTile, futureHist) =>
        imageRoute(futureTile, futureHist)
      } ~
      (pathPrefix("ndvi") & layerTile(prefix, layerId)){ futureTile =>
        modelRoute(futureTile, NDVI, Some(NDVI.colorRamp), Some(NDVI.breaks))
      } ~
      (pathPrefix("ndwi") & layerTile(prefix, layerId)){ futureTile =>
        modelRoute(futureTile, NDWI, Some(NDWI.colorRamp), Some(NDWI.breaks))
      } ~
      (pathPrefix("grey") & layerTile(prefix, layerId)){ futureTile =>
        modelRoute(futureTile, _.band(0), Some(ColorRamp(Array(0x000000, 0xFFFFFF))))
      }
    }

  def imageRoute(futureTile: Future[MultibandTile], futureHist: Future[Array[Histogram[Double]]]): Route =
    imageParams { params =>
      complete {
        for {
          tile <- futureTile
          layerHist <- futureHist
        } yield {
          val rgbHist = Array(layerHist(params.redBand), layerHist(params.greenBand), layerHist(params.blueBand))
          val rgbTile = tile.subsetBands(params.redBand, params.greenBand, params.blueBand)

          val png = Render(rgbTile, rgbHist, params)
          HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), png.bytes))
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
            val png = index(subsetTile).renderPng(colorMap)
            HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`image/png`), png.bytes))
          }
        }
      }
    }
}

object Routes extends Routes