package com.rasterfoundry.backsplash.io

import cats.effect.{IO, Timer}
import cats.data._
import cats.implicits._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.nodes.ProjectNode
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.database.SceneToProjectDao
import com.rasterfoundry.datamodel.{
  MosaicDefinition,
  SceneType,
  SingleBandOptions
}
import doobie.implicits._
import fs2.Stream
import geotrellis.raster.{Raster, io => _, _}
import geotrellis.spark.tiling.LayoutLevel
import geotrellis.vector.{Extent, Projected}

import scala.concurrent.ExecutionContext.Implicits.global

object Mosaic extends RollbarNotifier {

  import com.rasterfoundry.database.util.RFTransactor.xa

  implicit val timer: Timer[IO] = IO.timer(global)

  def getMosaicDefinitions(self: ProjectNode,
                           extent: Extent): Stream[IO, MosaicDefinition] = {
    self.getBandOverrides match {
      case Some((red, green, blue)) =>
        SceneToProjectDao
          .getMosaicDefinition(
            self.projectId,
            Some(Projected(extent, 3857)),
            Some(red),
            Some(green),
            Some(blue)
          )
          .transact(xa)
      case None =>
        SceneToProjectDao
          .getMosaicDefinition(
            self.projectId,
            Some(Projected(extent, 3857))
          )
          .transact(xa)
    }
  }

  def getMultiBandTileFromMosaic(z: Int, x: Int, y: Int, extent: Extent)(
      md: MosaicDefinition): IO[Option[Raster[Tile]]] =
    md.sceneType match {
      case Some(SceneType.COG) =>
        (Cog.fetchMultiBandCogTile(md, z, x, y, extent) map { maybeResample(_) }).value
      case Some(SceneType.Avro) =>
        (Avro.fetchMultiBandAvroTile(md, z, x, y, extent) map {
          maybeResample(_)
        }).value
      case None =>
        throw UnknownSceneTypeException(
          "Unable to fetch tiles with unknown scene type")
    }

  def getMosaicDefinitionTile(self: ProjectNode,
                              z: Int,
                              x: Int,
                              y: Int,
                              extent: Extent,
                              md: MosaicDefinition): IO[Option[Raster[Tile]]] =
    if (!self.isSingleBand) { getMultiBandTileFromMosaic(z, x, y, extent)(md) } else {
      logger.info(
        s"Getting Single Band Tile From Mosaic: ${z} ${x} ${y} ${self.projectId}")
      getSingleBandTileFromMosaic(
        z,
        x,
        y,
        extent,
        self.singleBandOptions getOrElse {
          throw SingleBandOptionsException(
            "No single-band options found for single-band visualization")
        },
        self.rawSingleBandValues
      )(md)
    }

  def getSingleBandTileFromMosaic(z: Int,
                                  x: Int,
                                  y: Int,
                                  extent: Extent,
                                  singleBandOptions: SingleBandOptions.Params,
                                  rawSingleBandValues: Boolean)(
      md: MosaicDefinition): IO[Option[Raster[Tile]]] =
    md.sceneType match {
      case Some(SceneType.COG) =>
        (Cog
          .fetchSingleBandCogTile(
            md,
            z,
            x,
            y,
            extent,
            singleBandOptions,
            rawSingleBandValues) map { maybeResample(_) }).value
      case Some(SceneType.Avro) =>
        (Avro
          .fetchSingleBandAvroTile(
            md,
            z,
            x,
            y,
            extent,
            singleBandOptions,
            rawSingleBandValues) map { maybeResample(_) }).value
      case None =>
        throw UnknownSceneTypeException(
          "Unable to fetch tiles with unknown scene type")
    }

  def getMosaicTileForExtent(
      extent: Extent,
      cellSize: CellSize,
      singleBandOptions: Option[SingleBandOptions.Params],
      singleBand: Boolean)(md: MosaicDefinition): IO[Option[Raster[Tile]]] = {
    md.sceneType match {
      case Some(SceneType.COG) =>
        Cog.tileForExtent(extent, cellSize, singleBandOptions, singleBand, md)
      case Some(SceneType.Avro) =>
        Avro.tileForExtent(extent, cellSize, singleBandOptions, singleBand, md)
      case None =>
        throw UnknownSceneTypeException(
          "Unable to fetch tiles with unknown scene type")
    }
  }

  @inline def maybeResample(tile: Raster[Tile]): Raster[Tile] =
    if (tile.dimensions != (256, 256)) tile.resample(256, 256) else tile
}
