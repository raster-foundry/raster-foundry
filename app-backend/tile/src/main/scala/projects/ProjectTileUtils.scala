package com.azavea.rf.tile.project

import java.util.UUID

import com.azavea.rf.common.KamonTraceRF
import com.azavea.rf.datamodel.MosaicDefinition

import akka.dispatch.MessageDispatcher
import cats.data._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.vector._
import geotrellis.vector.io._

import scala.concurrent._
import scala.util._


trait ProjectTileUtils extends LazyLogging with KamonTraceRF {
  implicit val blockingDispatcher: MessageDispatcher
  val store: PostgresAttributeStore

  /** Get raster extent for a given scene
    *
    * @param tileMetadata
    * @return
    */
  def getDefinedRasterExtent(tileMetadata: TileLayerMetadata[_]): RasterExtent = {
    val rasterExtent = RasterExtent(tileMetadata.layout.extent,
      tileMetadata.layout.tileLayout.totalCols.toInt,
      tileMetadata.layout.tileLayout.totalRows.toInt
    )
    val gridBounds = rasterExtent.gridBoundsFor(tileMetadata.extent)
    rasterExtent.rasterExtentFor(gridBounds).toRasterExtent
  }

  /** Get the minimum zoom level for a single scene from which a histogram can be constructed without
    * losing too much information (too much being defined by the 'size' threshold)
    *
    * @param sceneId
    * @param pixelSize
    * @return
    */
  def findMinAcceptableSceneZoom(sceneId: UUID, pixelSize: Int = 512): Option[(Extent, Int)] = {
    def startZoom(zoom: Int): Option[(Extent, Int)] = {
      val currentId = LayerId(sceneId.toString, zoom)
      val metadata = Try {
        store.readMetadata[TileLayerMetadata[SpatialKey]](currentId)
      }.toOption
      metadata.flatMap { meta =>
        val re = getDefinedRasterExtent(meta)
        logger.debug(s"Data Extent: ${meta.extent.reproject(WebMercator, LatLng).toGeoJson()}")
        logger.debug(s"$currentId has (${re.cols},${re.rows}) pixels")
        if (re.cols >= pixelSize || re.rows >= pixelSize) Some((meta.extent, currentId.zoom))
        else startZoom(zoom + 1).orElse(Some(meta.extent, currentId.zoom))
      }
    }
    startZoom(1)
  }

  /** Get the minimum zoom level for a project from which a histogram can be constructed without
    * losing too much information (too much being defined by the 'size' threshold)
    *
    * @param pixelSize
    * @param mosaicDefinitions
    * @return
    */
  def findMinAcceptableProjectZoom(pixelSize: Int = 512, mosaicDefinitions: OptionT[Future, Seq[MosaicDefinition]]): OptionT[Future, (Extent, Int)] = {
    mosaicDefinitions.semiflatMap({ mosaic =>
      Future.sequence(mosaic.map { case MosaicDefinition(sceneId, _) =>
        Future {
          findMinAcceptableSceneZoom(sceneId, 256)
        }
      })
    }).map({ zoomsAndExtents =>
      zoomsAndExtents.flatten.reduce({ (agg, next) =>
        val e1 = agg._1
        val e2 = next._1
        (Extent(
          e1.xmin min e2.xmin,
          e1.ymin min e2.ymin,
          e1.xmax max e2.xmax,
          e1.ymax max e2.ymax
        ), agg._2 max next._2)
      })
    })
  }
}
