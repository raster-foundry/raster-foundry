package com.azavea.rf.tile.tool

import com.azavea.rf.tile._
import com.azavea.rf.tile.image.Mosaic
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.maml._
import com.azavea.maml.ast._
import com.azavea.maml.eval._
import com.azavea.maml.eval.tile._
import com.azavea.maml.util.NeighborhoodConversion
import cats._
import cats.data.Validated._
import cats.data.{NonEmptyList => NEL, _}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4.WebMercator
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import geotrellis.spark.tiling._
import geotrellis.vector.{Extent, MultiPolygon}
import geotrellis.slick.Projected
import geotrellis.spark.io.postgres.PostgresAttributeStore

import scala.util.{Failure, Success, Try}
import java.util.UUID

import cats.effect.IO
import com.azavea.rf.database.util.RFTransactor
import doobie.util.transactor.Transactor

import scala.concurrent.{ExecutionContext, Future}


/** This interpreter handles resource resolution and compilation of MapAlgebra ASTs */
class TileResolver(xaa: Transactor[IO], ec: ExecutionContext) extends LazyLogging {

  implicit val execution: ExecutionContext = ec
  implicit val xa: Transactor[IO] = xaa

  val store = PostgresAttributeStore()

  val intNdTile = IntConstantTile(NODATA, 256, 256)

  def resolveBuffered(fullExp: Expression): (Int, Int, Int) => Future[Interpreted[Expression]] = {

    def eval(exp: Expression, buffer: Int): (Int, Int, Int) => Future[Interpreted[Expression]] = (z: Int, x: Int, y: Int) => {
      lazy val extent = TileLayouts(z).mapTransform(SpatialKey(x, y))
      exp match {
        case pr@ProjectRaster(projId, None, celltype) =>
          Future.successful(Invalid(NEL.of(NonEvaluableNode(exp, Some("no band given")))))
        case pr@ProjectRaster(projId, Some(band), celltype) =>
          lazy val ndtile = celltype match {
            case Some(ct) => intNdTile.convert(ct)
            case None => intNdTile
          }
          val futureSource = if (buffer > 0) {
            (for {
              tl <- Mosaic.raw(projId, z, x - 1, y - 1)
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                      .orElse(OptionT.pure[Future](ndtile))
              tm <- Mosaic.raw(projId, z, x, y - 1)
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                      .orElse(OptionT.pure[Future](ndtile))
              tr <- Mosaic.raw(projId, z, x, y - 1)
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                      .orElse(OptionT.pure[Future](ndtile))
              ml <- Mosaic.raw(projId, z, x - 1, y)
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                      .orElse(OptionT.pure[Future](ndtile))
              mm <- Mosaic.raw(projId, z, x, y)
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
              mr <- Mosaic.raw(projId, z, x + 1, y)
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                      .orElse(OptionT.pure[Future](ndtile))
              bl <- Mosaic.raw(projId, z, x - 1, y + 1)
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                      .orElse(OptionT.pure[Future](ndtile))
              bm <- Mosaic.raw(projId, z, x, y + 1)
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                      .orElse(OptionT.pure[Future](ndtile))
              br <- Mosaic.raw(projId, z, x + 1, y + 1)
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                      .orElse(OptionT.pure[Future](ndtile))
            } yield {
              TileWithNeighbors(mm, Some(NeighboringTiles(tl, tm, tr, ml, mr,bl, bm, br)))
                .withBuffer(buffer)
            })
          } else {
            Mosaic.raw(projId, z, x, y)
              .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
          }

          futureSource.value.map({ maybeSource =>
            maybeSource match {
              case Some(tile) => Valid(TileLiteral(tile, RasterExtent(tile, extent)))
              case None => Invalid(NEL.of(UnknownTileResolutionError(exp, Some((z, x, y)))))
            }
          })
        case sr@SceneRaster(sceneId, None, celltype) =>
          Future.successful(Invalid(NEL.of(NonEvaluableNode(exp, Some("no band given")))))
        case sr@SceneRaster(sceneId, Some(band), celltype) =>
          lazy val ndtile = celltype match {
            case Some(ct) => intNdTile.convert(ct)
            case None => intNdTile
          }
          val futureSource = if (buffer > 0)
            (for {
              tl <- LayerCache.layerTile(sceneId, z, SpatialKey(x - 1, y - 1))
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                      .orElse(OptionT.pure[Future](ndtile))
              tm <- LayerCache.layerTile(sceneId, z, SpatialKey(x, y - 1))
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                      .orElse(OptionT.pure[Future](ndtile))
              tr <- LayerCache.layerTile(sceneId, z, SpatialKey(x + 1 , y - 1))
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                      .orElse(OptionT.pure[Future](ndtile))
              ml <- LayerCache.layerTile(sceneId, z, SpatialKey(x - 1, y))
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                      .orElse(OptionT.pure[Future](ndtile))
              mm <- LayerCache.layerTile(sceneId, z, SpatialKey(x, y))
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
              mr <- LayerCache.layerTile(sceneId, z, SpatialKey(x + 1, y))
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                      .orElse(OptionT.pure[Future](ndtile))
              bl <- LayerCache.layerTile(sceneId, z, SpatialKey(x - 1, y + 1))
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                      .orElse(OptionT.pure[Future](ndtile))
              bm <- LayerCache.layerTile(sceneId, z, SpatialKey(x, y + 1))
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                      .orElse(OptionT.pure[Future](ndtile))
              br <- LayerCache.layerTile(sceneId, z, SpatialKey(x + 1, y + 1))
                      .map({ tile => tile.band(band).interpretAs(celltype.getOrElse(tile.cellType)) })
                      .orElse(OptionT.pure[Future](ndtile))
            } yield {
              TileWithNeighbors(mm, Some(NeighboringTiles(tl, tm, tr, ml, mr,bl, bm, br)))
                .withBuffer(buffer)
            })
          else
            LayerCache.layerTile(sceneId, z, SpatialKey(x, y)).map({ tile =>
              tile.band(band).interpretAs(celltype.getOrElse(tile.cellType))
            })
          futureSource.value.map({ maybeTile =>
            maybeTile match {
              case Some(tile) => Valid(TileLiteral(tile, RasterExtent(tile, extent)))
              case None => Invalid(NEL.of(UnknownTileResolutionError(exp, Some((z, x, y)))))
            }
          })

        case f: FocalExpression =>
          exp.children
            .map({ child => eval(child, buffer + NeighborhoodConversion(f.neighborhood).extent)(z, x, y) })
            .toList.sequence
            .map({ futureValidChildren => futureValidChildren.toList.sequence })
            .map({ children =>
              children.map({ exp.withChildren(_) })
            })
        case _ =>
          exp.children
            .map({ child => eval(child, buffer)(z, x, y) })
            .toList.sequence
            .map({ futureValidChildren => futureValidChildren.toList.sequence })
            .map({ children =>
              children.map({ exp.withChildren(_) })
            })
      }
    }
    eval(fullExp, 0)
  }


  def resolveForExtent(fullExp: Expression, zoom: Int, extent: Extent): Future[Interpreted[Expression]] = {
    fullExp match {
      case sr@SceneRaster(sceneId, None, celltype) =>
        Future.successful(Invalid(NEL.of(NonEvaluableNode(fullExp, Some("no band given")))))
      case pr@ProjectRaster(projId, None, celltype) =>
        Future.successful(Invalid(NEL.of(NonEvaluableNode(fullExp, Some("no band given")))))
      case sr@SceneRaster(sceneId, Some(band), celltype) =>
        Future.successful({
          Try {
            val layerId = LayerId(sceneId.toString, zoom)

            S3CollectionLayerReader(store)
              .query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](layerId)
              .result
              .stitch
              .crop(extent)
              .tile
          } match {
            case Success(tile) =>
              val t = tile.band(band).interpretAs(celltype.getOrElse(tile.cellType))
              Valid(TileLiteral(t, RasterExtent(t, extent)))
            case Failure(e) =>
              Invalid(NEL.of(UnknownTileResolutionError(fullExp, None)))
          }
        })
      case pr@ProjectRaster(projId, Some(band), celltype) =>
        Mosaic.rawForExtent(projId, zoom, Some(Projected(extent.toPolygon, 3857))).value.map({ maybeTile =>
          maybeTile match {
            case Some(tile) =>
              val t = tile.band(band).interpretAs(celltype.getOrElse(tile.cellType))
              Valid(TileLiteral(t, RasterExtent(t, extent)))
            case None =>
              Invalid(NEL.of(UnknownTileResolutionError(fullExp, None)))
          }
        })
      case _ =>
        fullExp.children
          .map({ child => resolveForExtent(child, zoom, extent) })
          .toList.sequence
          .map({ futureValidChildren => futureValidChildren.toList.sequence })
          .map({ children =>
            children.map({ fullExp.withChildren(_) })
          })
    }
  }
}

