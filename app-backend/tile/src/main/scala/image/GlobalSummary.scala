package com.azavea.rf.tile.image

import java.util.UUID

import cats.data._
import cats.effect.IO
import cats.implicits._
import com.azavea.rf.common.utils.CogUtils
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.datamodel.{MosaicDefinition, SceneType}
import com.azavea.rf.tile._
import com.typesafe.scalalogging.LazyLogging
import doobie.util.transactor.Transactor
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.reproject._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.spark.tiling._
import geotrellis.vector.Extent
import geotrellis.vector.io._

import scala.concurrent._
import scala.util._

object GlobalSummary extends LazyLogging {
  val system = AkkaSystem.system
  implicit val blockingDispatcher =
    system.dispatchers.lookup("blocking-dispatcher")
  implicit lazy val xa = RFTransactor.xa

  val store = PostgresAttributeStore()

  /** Get the [[RasterExtent]] which describes the meaningful subset of a layer from metadata */
  private def getDefinedRasterExtent(md: TileLayerMetadata[_]): RasterExtent = {
    val re = RasterExtent(md.layout.extent,
                          md.layout.tileLayout.totalCols.toInt,
                          md.layout.tileLayout.totalRows.toInt)
    val gb = re.gridBoundsFor(md.extent)
    re.rasterExtentFor(gb).toRasterExtent
  }

  /** Get the minimum zoom level for a single scene from which a histogram can be constructed without
    * losing too much information (too much being defined by the 'size' threshold)
    */
  def minAcceptableSceneZoom(sceneId: UUID,
                             store: AttributeStore,
                             size: Int): Option[(Extent, Int)] = {
    def startZoom(zoom: Int): Option[(Extent, Int)] = {
      val currentId = LayerId(sceneId.toString, zoom)
      val metadata = Try {
        store.readMetadata[TileLayerMetadata[SpatialKey]](currentId)
      }.toOption
      metadata.flatMap { meta =>
        val re = getDefinedRasterExtent(meta)
        logger.debug(
          s"Data Extent: ${meta.extent.reproject(WebMercator, LatLng).toGeoJson()}")
        logger.debug(s"$currentId has (${re.cols},${re.rows}) pixels")
        if (re.cols >= size || re.rows >= size)
          Some((meta.extent, currentId.zoom))
        else startZoom(zoom + 1).orElse(Some(meta.extent, currentId.zoom))
      }
    }

    startZoom(1)
  }

  /** Get the minimum zoom level for a single scene from which a histogram can be constructed without
    * losing too much information (too much being defined by the 'size' threshold)
    */
  def minAcceptableCogZoom(uri: String,
                           size: Int): OptionT[Future, (Extent, Int)] = {
    // This is currently somewhat hacky because Tiffs are quite different than fleshed out layers
    // In particular, the `int` here is not a zoom but an index to this Cog's least resolute overview
    for {
      tiff <- CogUtils.fromUri(uri)
      minOverview <- OptionT.fromOption[Future] {
        tiff.overviews.headOption.map { _ =>
          tiff.overviews.maxBy(_.cellSize.resolution)
        }
      }
    } yield {
      // TODO: make use of size
      val extent = minOverview.extent
      val poly = extent.toPolygon().reproject(minOverview.crs, WebMercator)
      val latlngpoly = extent.toPolygon().reproject(minOverview.crs, LatLng)
      val wmRE = ReprojectRasterExtent(minOverview.rasterExtent,
                                       minOverview.crs,
                                       WebMercator)
      val scheme = ZoomedLayoutScheme(WebMercator, 256)
      val zoom = scheme.levelFor(wmRE.extent, wmRE.cellSize).zoom
      (wmRE.extent, zoom)
    }
  }

  /** Get the minimum zoom level for a project from which a histogram can be constructed without
    * losing too much information (too much being defined by the 'size' threshold)
    */
  def minAcceptableProjectZoom(
      projId: UUID,
      size: Int = 512
  )(implicit xa: Transactor[IO], ec: ExecutionContext): Future[(Extent, Int)] =
    // TODO this should be updated to handle both multi band and single band mosaics
    MultiBandMosaic
      .mosaicDefinition(projId)
      .flatMap({ mosaic =>
        Future.sequence(
          mosaic.map {
            case MosaicDefinition(sceneId,
                                  _,
                                  maybeSceneType,
                                  Some(ingestLocation)) =>
              maybeSceneType match {
                case Some(SceneType.COG) =>
                  minAcceptableCogZoom(ingestLocation, size).value
                case _ =>
                  Future { minAcceptableSceneZoom(sceneId, store, size) }
              }
            case _ => Future { None }
          }
        )
      })
      .map({ zoomsAndExtents =>
        logger.debug(s"ZOOMANDEXTENTS: $zoomsAndExtents")
        zoomsAndExtents.flatten.reduce({ (agg, next) =>
          val e1 = agg._1
          val e2 = next._1
          (Extent(
             e1.xmin min e2.xmin,
             e1.ymin min e2.ymin,
             e1.xmax max e2.xmax,
             e1.ymax max e2.ymax
           ),
           agg._2 max next._2)
        })
      })

}
