package com.azavea.rf.tile.image

import com.azavea.rf.tile._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.ScenesToProjects
import com.azavea.rf.datamodel.{MosaicDefinition, WhiteBalance}
import com.azavea.rf.common.cache._

import com.github.blemale.scaffeine.{ Cache => ScaffeineCache, Scaffeine }
import geotrellis.raster._
import geotrellis.raster.io._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.raster.GridBounds
import geotrellis.proj4._
import geotrellis.slick.Projected
import geotrellis.vector.{Polygon, Extent}
import geotrellis.vector.io._
import cats.data._
import cats.implicits._

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._


case class TagWithTTL(tag: String, ttl: Duration)

object Mosaic {
  val memcachedClient = LayerCache.memcachedClient
  val memcached = HeapBackedMemcachedClient(LayerCache.memcachedClient)

  def tileLayerMetadata(id: UUID, zoom: Int)(implicit database: Database): OptionT[Future, (Int, TileLayerMetadata[SpatialKey])] = {
    LayerCache.attributeStoreForLayer(id).mapFilter { case (store, pyramidMaxZoom) =>
      // because metadata attributes are cached in AttributeStore itself, there is no point caching this function
      val layerName = id.toString
      for (maxZoom <- pyramidMaxZoom.get(layerName)) yield {
        val z = if (zoom > maxZoom) maxZoom else zoom
        blocking {
          z -> store.readMetadata[TileLayerMetadata[SpatialKey]](LayerId(layerName, z))
        }
      }
    }
  }

  def mosaicDefinition(projectId: UUID, tagttl: Option[TagWithTTL])(implicit database: Database): OptionT[Future, Seq[MosaicDefinition]] = {
    val cacheKey = tagttl match {
      case Some(t) => s"mosaic-definition-$projectId-${t.tag}"
      case None => s"mosaic-definition-$projectId"
    }

    memcached.cachingOptionT(cacheKey) { _ =>
      OptionT(ScenesToProjects.getMosaicDefinition(projectId))
    }
  }

  /** Fetch the tile for given resolution. If it is not present, use a tile from a lower zoom level */
  def fetch(id: UUID, zoom: Int, col: Int, row: Int)(implicit database: Database): OptionT[Future, MultibandTile] =
    tileLayerMetadata(id, zoom).flatMap { case (sourceZoom, tlm) =>
      val zoomDiff = zoom - sourceZoom
      val resolutionDiff = 1 << zoomDiff
      val sourceKey = SpatialKey(col / resolutionDiff, row / resolutionDiff)
      if (tlm.bounds.includes(sourceKey)) {
        LayerCache.layerTile(id, sourceZoom, sourceKey).map { tile =>
          val innerCol = col % resolutionDiff
          val innerRow = row % resolutionDiff
          val cols = tile.cols / resolutionDiff
          val rows = tile.rows / resolutionDiff
          tile.crop(GridBounds(
            colMin = innerCol * cols,
            rowMin = innerRow * rows,
            colMax = (innerCol + 1) * cols - 1,
            rowMax = (innerRow + 1) * rows - 1
          )).resample(256, 256)
        }
      } else {
        OptionT.none[Future, MultibandTile]
      }
    }

  /** Fetch the tile for the given zoom level and bbox
    * If no bbox is specified, it will use the project tileLayerMetadata layoutExtent
    */
  def fetchRenderedExtent(id: UUID, zoom: Int, bbox: Option[Projected[Polygon]])(implicit database: Database): OptionT[Future, MultibandTile] = {
    tileLayerMetadata(id, zoom).flatMap { case (sourceZoom, tlm) =>
      val extent: Extent =
        bbox.map { case Projected(poly, srid) =>
          poly.envelope.reproject(CRS.fromEpsgCode(srid), tlm.crs)
        }.getOrElse(tlm.layoutExtent)

      LayerCache.layerTileForExtent(id, sourceZoom, extent)
    }
  }

  /** Fetch all bands of a [[MultibandTile]] for the given extent and return them without assuming anything of their semantics */
  def rawForExtent(projectId: UUID, zoom: Int, bbox: Option[Projected[Polygon]])(implicit database: Database): OptionT[Future, MultibandTile] = {
    mosaicDefinition(projectId, None).flatMap { mosaic =>
      val mayhapTiles: Seq[OptionT[Future, MultibandTile]] =
        for (MosaicDefinition(sceneId, _) <- mosaic) yield Mosaic.fetchRenderedExtent(sceneId, zoom, bbox)

      val futureMergeTile: Future[Option[MultibandTile]] =
        Future.sequence(mayhapTiles.map(_.value)).map { maybeTiles =>
          val tiles = maybeTiles.flatten
          if (tiles.nonEmpty)
            Option(tiles.reduce(_ merge _))
          else
            Option.empty[MultibandTile]
        }

      OptionT(futureMergeTile)
    }
  }

  /** Fetch all bands of a [[MultibandTile]] and return them without assuming anything of their semantics */
  def raw(projectId: UUID, zoom: Int, col: Int, row: Int)(implicit database: Database): OptionT[Future, MultibandTile] = {
    mosaicDefinition(projectId, None).flatMap { mosaic =>
      val mayhapTiles: Seq[OptionT[Future, MultibandTile]] =
        for (MosaicDefinition(sceneId, _) <- mosaic) yield Mosaic.fetch(sceneId, zoom, col, row)


      val futureMergeTile: Future[Option[MultibandTile]] =
        Future.sequence(mayhapTiles.map(_.value)).map { maybeTiles =>
          val tiles = maybeTiles.flatten
          if (tiles.nonEmpty)
            Option(tiles.reduce(_ merge _))
          else
            Option.empty[MultibandTile]
        }

      OptionT(futureMergeTile)
    }
  }

  /**   Render a png from TMS pyramids given that they are in the same projection.
    *   If a layer does not go up to requested zoom it will be up-sampled.
    *   Layers missing color correction in the mosaic definition will be excluded.
    *   The size of the image will depend on the selected zoom and bbox.
    *
    *   Note:
    *   Currently, if the render takes too long, it will time out. Given enough requests, this
    *   could cause us to essentially ddos ourselves, so we probably want to change
    *   this from a simple endpoint to an airflow operation: IE the request kicks off
    *   a render job then returns the job id
    *
    *   @param zoomOption  the zoom level to use
    *   @param bboxOption the bounding box for the image
    *   @param colorCorrect setting to determine if color correction should be applied
    */
  def render(
    projectId: UUID, zoomOption: Option[Int], bboxOption: Option[String],
    colorCorrect: Boolean = true)(implicit database: Database): OptionT[Future, MultibandTile] = {

    val bboxPolygon: Option[Projected[Polygon]] =
      try {
        bboxOption map { bbox =>
          Projected(Extent.fromString(bbox).toPolygon(), 4326).reproject(LatLng, WebMercator)(3857)
        }
      } catch {
        case e: Exception =>
          throw new IllegalArgumentException("Four comma separated coordinates must be given for bbox").initCause(e)
      }

    val zoom: Int = zoomOption.getOrElse(8)

    mosaicDefinition(projectId, None).flatMap { mosaic =>
      val futureTiles: Future[Seq[MultibandTile]] = {
        val tiles = mosaic.flatMap { case MosaicDefinition(sceneId, maybeColorCorrectParams) =>
          maybeColorCorrectParams.map { colorCorrectParams =>
            Mosaic.fetchRenderedExtent(sceneId, zoom, bboxPolygon).flatMap { tile =>
              if (colorCorrect) {
                LayerCache.layerHistogram(sceneId, zoom).map { hist =>
                  colorCorrectParams.colorCorrect(tile, hist)
                }
              } else {
                OptionT[Future, MultibandTile](Future(Some(tile)))
              }

            }.value
          }.toSeq
        }
        Future.sequence(tiles).map(_.flatten)
      }

      val futureMergeTile =
        for {
          doColorCorrect <- hasColorCorrection(projectId)
          tiles <- futureTiles
        } yield colorCorrectAndMergeTiles(tiles, doColorCorrect)

      OptionT(futureMergeTile)
    }
  }

  /** Mosaic tiles from TMS pyramids given that they are in the same projection.
    *   If a layer does not go up to requested zoom it will be up-sampled.
    *   Layers missing color correction in the mosaic definition will be excluded.
    *
    *   @param rgbOnly  This parameter determines whether or not the mosaic should return an RGB
    *                    MultibandTile or all available bands, regardless of their semantics.
    */
  def apply(
    projectId: UUID,
    zoom: Int, col: Int, row: Int,
    tag: Option[String] = None,
    rgbOnly: Boolean = true
  )(
    implicit database: Database
  ): OptionT[Future, MultibandTile] = {
    // Lookup project definition
    // tag present, include in lookup to re-use cache
    // no tag to control cache rollover, so don't cache
    mosaicDefinition(projectId, tag.map(s => TagWithTTL(tag=s, ttl=60.seconds))).flatMap { mosaic =>
      val futureTiles: Future[Seq[MultibandTile]] = {
        val tiles = mosaic.flatMap { case MosaicDefinition(sceneId, maybeColorCorrectParams) =>
          if (rgbOnly) {
            maybeColorCorrectParams.map { colorCorrectParams =>
              Mosaic.fetch(sceneId, zoom, col, row).flatMap { tile =>
                LayerCache.layerHistogram(sceneId, zoom).map { hist =>
                  colorCorrectParams.colorCorrect(tile, hist)
                }
              }.value
            }
          } else {
            // Wrap in List so it can flattened by the same flatMap above
            List(Mosaic.fetch(sceneId, zoom, col, row).value)
          }
        }.toSeq
        Future.sequence(tiles).map(_.flatten)
      }

      val futureMergeTile =
        for {
          doColorCorrect <- hasColorCorrection(projectId)
          tiles <- futureTiles
        } yield colorCorrectAndMergeTiles(tiles, doColorCorrect)

      OptionT(futureMergeTile)
    }
  }

  /** Check to see if a project has color correction; if this isn't specified, default to false */
  def hasColorCorrection(projectId: UUID)(implicit database: Database) =
    mosaicDefinition(projectId, None).map { mosaic =>
      mosaic.flatMap { case MosaicDefinition(sceneId, maybeColorCorrectParams) =>
        maybeColorCorrectParams.map(_.autoBalance.enabled)
      }.foldLeft(true)((acc, x) => acc && x)
    }.value.map {
      case Some(true) => true
      case _ => false
    }

  /** Merge tiles together, optionally color correcting */
  def colorCorrectAndMergeTiles(tiles: Seq[MultibandTile], doColorCorrect: Boolean): Option[MultibandTile] = {
    val newTiles =
      if (doColorCorrect)
        WhiteBalance(tiles.toList)
      else
        tiles

    if (newTiles.isEmpty)
      None
    else
      Some(newTiles.reduce(_ merge _))
  }
}
