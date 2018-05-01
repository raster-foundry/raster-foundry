package com.azavea.rf.tile.image

import com.azavea.rf.tile._
import com.azavea.rf.datamodel.MosaicDefinition
import geotrellis.raster._
import geotrellis.vector.io._
import geotrellis.spark.io._
import geotrellis.spark._
import geotrellis.proj4._
import geotrellis.vector.Extent
import com.typesafe.scalalogging.LazyLogging
import cats.data._
import cats.implicits._
import cats.effect.IO
import java.util.UUID
import doobie.util.transactor.Transactor

import com.azavea.rf.database.util.RFTransactor
import geotrellis.spark.io.postgres.PostgresAttributeStore

import scala.concurrent._
import scala.util._

object GlobalSummary extends LazyLogging {
  val system = AkkaSystem.system
  implicit val blockingDispatcher = system.dispatchers.lookup("blocking-dispatcher")
  implicit lazy val xa = RFTransactor.xa

  val store = PostgresAttributeStore()

  /** Get the [[RasterExtent]] which describes the meaningful subset of a layer from metadata */
  private def getDefinedRasterExtent(md: TileLayerMetadata[_]): RasterExtent = {
    val re = RasterExtent(md.layout.extent,
      md.layout.tileLayout.totalCols.toInt,
      md.layout.tileLayout.totalRows.toInt
    )
    val gb = re.gridBoundsFor(md.extent)
    re.rasterExtentFor(gb).toRasterExtent
  }

  /** Get the minimum zoom level for a single scene from which a histogram can be constructed without
    *  losing too much information (too much being defined by the 'size' threshold)
    */
  def minAcceptableSceneZoom(sceneId: UUID, store: AttributeStore, size: Int = 512): Option[(Extent, Int)] = {
    def startZoom(zoom: Int): Option[(Extent, Int)] = {
      val currentId = LayerId(sceneId.toString, zoom)
      val metadata = Try { store.readMetadata[TileLayerMetadata[SpatialKey]](currentId) }.toOption
      metadata.flatMap { meta =>
        val re = getDefinedRasterExtent(meta)
        logger.debug(s"Data Extent: ${meta.extent.reproject(WebMercator, LatLng).toGeoJson()}")
        logger.debug(s"$currentId has (${re.cols},${re.rows}) pixels")
        if (re.cols >= size || re.rows >= size) Some((meta.extent, currentId.zoom))
        else startZoom(zoom + 1).orElse(Some(meta.extent, currentId.zoom))
      }
    }
    startZoom(1)
  }

  /** Get the minimum zoom level for a project from which a histogram can be constructed without
    *  losing too much information (too much being defined by the 'size' threshold)
    */
  def minAcceptableProjectZoom(
    projId: UUID,
    size: Int = 512
  )(implicit xa: Transactor[IO], ec: ExecutionContext): Future[(Extent, Int)] =
    // TODO this should be updated to handle both multi band and single band mosaics
    MultiBandMosaic.mosaicDefinition(projId).flatMap({ mosaic =>
      Future.sequence(mosaic.map { case MosaicDefinition(sceneId, _) =>
        Future {
          minAcceptableSceneZoom(sceneId, store, 256)
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
