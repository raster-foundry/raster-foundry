package com.rasterfoundry.backsplash.export

import com.rasterfoundry.common.datamodel.export._
import Exportable.ops._
import TileReification._

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.compression._
import geotrellis.proj4._
import geotrellis.raster.rasterize.Rasterizer
import geotrellis.server._
import com.azavea.maml.ast.Expression
import com.azavea.maml.ast.codec.tree._
import com.azavea.maml.eval._
import cats._
import cats.data.Validated._
import cats.effect._
import com.typesafe.scalalogging._

trait ExportableInstances extends LazyLogging {
  implicit def exportableAnalysis =
    new Exportable[ExportDefinition[AnalysisExportSource]] {
      def keyedTileSegments(
          self: ExportDefinition[AnalysisExportSource],
          zoom: Int
      )(
          implicit cs: ContextShift[IO]
      ): Iterator[((Int, Int), MultibandTile)] = {
        val extent = exportExtent(self)
        val tileList = TilesForExtent.latLng(extent, zoom)
        val minTileX = tileList.map(_._1).min
        val minTileY = tileList.map(_._2).min

        new Iterator[((Int, Int), MultibandTile)] {
          var allTiles = tileList
          val eval =
            LayerTms.apply(IO.pure(self.source.ast),
                           IO.pure(self.source.params),
                           BufferingInterpreter.DEFAULT)

          def next() = {
            val (x, y) = allTiles.head
            allTiles = allTiles.drop(1)

            logger.debug(s"Requesting Tile @$zoom/$x/${y}")
            val tile = eval(zoom, x, y).unsafeRunSync match {
              case Valid(mbtile) =>
                logger.debug(s"Constructed Multiband tile @${zoom}/$x/$y")
                val tileExtent =
                  TileReification.tmsLevels(zoom).mapTransform.keyToExtent(x, y)
                mbtile.mask(
                  tileExtent,
                  List(self.source.area.reproject(LatLng, WebMercator)),
                  Rasterizer.Options.DEFAULT)
              case _ =>
                logger.debug(s"Generating empty tile @${zoom}/${x}/${y}")
                MultibandTile(TileReification.invisiTile)
            }
            val xLoc = x - minTileX
            val yLoc = y - minTileY
            logger.debug(s"Tiff segment Location: (${xLoc}, ${yLoc})")
            ((xLoc, yLoc), tile)
          }

          def hasNext = {
            logger.debug(s"${allTiles.length} tiles left")
            allTiles.length > 0
          }
        }
      }

      def exportCellType(
          self: ExportDefinition[AnalysisExportSource]): CellType =
        DoubleConstantNoDataCellType

      def exportZoom(self: ExportDefinition[AnalysisExportSource]): Int =
        self.source.zoom

      def exportExtent(self: ExportDefinition[AnalysisExportSource]) =
        self.source.area.envelope

      def exportDestination(self: ExportDefinition[AnalysisExportSource]) =
        self.output.destination

      def segmentLayout(self: ExportDefinition[AnalysisExportSource]) =
        exportSegmentLayout(self.source.area.envelope, self.source.zoom)
    }

  implicit val exportableMosaic =
    new Exportable[ExportDefinition[MosaicExportSource]] {
      def keyedTileSegments(
          self: ExportDefinition[MosaicExportSource],
          zoom: Int
      )(implicit cs: ContextShift[IO])
        : Iterator[((Int, Int), MultibandTile)] = {
        val extent = exportExtent(self)
        val tileList = TilesForExtent.latLng(extent, zoom)
        val minTileX = tileList.map(_._1).min
        val minTileY = tileList.map(_._2).min

        new Iterator[((Int, Int), MultibandTile)] {
          var allTiles = tileList
          val eval = LayerTms.identity(self.source.layers)

          def next() = {
            val (x, y) = allTiles.head
            allTiles = allTiles.drop(1)

            logger.debug(s"Requesting Tile @$zoom/$x/${y}")
            val tile = eval(zoom, x, y).unsafeRunSync match {
              case Valid(mbtile) =>
                logger.debug(
                  s"Constructed Multiband tile @${zoom}/$x/$y with bands ${mbtile.bandCount}")
                val tileExtent =
                  TileReification.tmsLevels(zoom).mapTransform.keyToExtent(x, y)
                mbtile.mask(
                  tileExtent,
                  List(self.source.area.reproject(LatLng, WebMercator)),
                  Rasterizer.Options.DEFAULT)
              case _ =>
                val bands = self.source.layers.head._2
                logger.debug(
                  s"Generating empty tile @${zoom}/${x}/${y} with bands ${bands.length}")
                MultibandTile(
                  bands.map(_ => TileReification.invisiTile).toArray)
            }
            val xLoc = x - minTileX
            val yLoc = y - minTileY
            logger.debug(s"Tiff segment Location: (${xLoc}, ${yLoc})")
            ((xLoc, yLoc), tile)
          }
          def hasNext = {
            logger.debug(s"${allTiles.length} tiles left")
            allTiles.length > 0
          }
        }
      }

      def exportZoom(self: ExportDefinition[MosaicExportSource]): Int =
        self.source.zoom

      def exportCellType(self: ExportDefinition[MosaicExportSource]) =
        DoubleConstantNoDataCellType

      def exportExtent(self: ExportDefinition[MosaicExportSource]) =
        self.source.area.envelope

      def exportDestination(self: ExportDefinition[MosaicExportSource]) =
        self.output.destination

      def segmentLayout(self: ExportDefinition[MosaicExportSource]) =
        exportSegmentLayout(self.source.area.envelope, self.source.zoom)
    }
}

object ExportableInstances extends ExportableInstances
