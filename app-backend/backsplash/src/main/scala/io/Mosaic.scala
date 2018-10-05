package com.azavea.rf.backsplash.io

import cats.effect.{IO, Timer}
import cats.implicits._
import com.azavea.rf.backsplash.nodes.ProjectNode
import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.database.SceneToProjectDao
import com.azavea.rf.datamodel.{MosaicDefinition, SceneType, SingleBandOptions}
import doobie.implicits._
import geotrellis.raster.{Raster, io => _, _}
import geotrellis.spark.tiling.LayoutLevel
import geotrellis.spark.{io => _}
import geotrellis.vector.{Extent, Projected}

import scala.concurrent.ExecutionContext.Implicits.global


object mosaic extends RollbarNotifier {

  import com.azavea.rf.database.util.RFTransactor.xa

  def getMosaicDefinitions(self: ProjectNode, extent: Extent): IO[Seq[MosaicDefinition]] = {
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

  def getMultiBandTileFromMosaic(z: Int, x: Int, y: Int, extent: Extent)(md: MosaicDefinition): IO[Option[Raster[Tile]]] =
    md.sceneType match {
      case Some(SceneType.COG) =>
        cog.fetchMultiBandCogTile(md, z, x, y, extent).value
      case Some(SceneType.Avro) =>
        avro.fetchMultiBandAvroTile(md, z, x, y, extent).value
      case None =>
        throw new Exception("Unable to fetch tiles with unknown scene type")
    }

  def getMosaicDefinitionTiles(self: ProjectNode, z: Int, x: Int, y: Int, extent: Extent, mds: Seq[MosaicDefinition]) = {
    mds.toList.parTraverse(self.isSingleBand match {
      case false =>
        getMultiBandTileFromMosaic(z, x, y, extent)
      case true => {
        logger.info(s"Getting Single Band Tile From Mosaic: ${z} ${x} ${y} ${self.projectId}")
        getSingleBandTileFromMosaic(z, x, y, extent, self.singleBandOptions getOrElse {
          throw new Exception(
              "No single-band options found for single-band visualization")
          }, self.rawSingleBandValues)
      }
    })
  }

  def getSingleBandTileFromMosaic(z: Int, x: Int, y: Int, extent: Extent, singleBandOptions: SingleBandOptions.Params, rawSingleBandValues: Boolean)(
    md: MosaicDefinition)(implicit t: Timer[IO]): IO[Option[Raster[Tile]]] =
    md.sceneType match {
      case Some(SceneType.COG) =>
        cog.fetchSingleBandCogTile(md, z, x, y, extent, singleBandOptions, rawSingleBandValues).value
      case Some(SceneType.Avro) =>
        avro.fetchSingleBandAvroTile(md, z, x, y, extent, singleBandOptions, rawSingleBandValues).value
      case None =>
        throw new Exception("Unable to fetch tiles with unknown scene type")
    }
}

