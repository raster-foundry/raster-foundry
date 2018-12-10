package com.rasterfoundry.backsplash

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.proj4.CRS
import geotrellis.server._

import com.azavea.maml.ast._
import com.azavea.maml.eval._

import cats._
import cats.implicits._
import cats.data.{NonEmptyList => NEL}
import cats.data.Validated._
import cats.effect._

import ProjectStore._
import ToolStore._
import ExtentReification._
import HasRasterExtents._
import TmsReification._

trait Implicits
    extends ToTmsReificationOps
    with ToExtentReificationOps
    with ToHasRasterExtentsOps
    with ToProjectStoreOps
    with ToToolStoreOps {

  implicit def paintableToolTmsReification[B: TmsReification] =
    new TmsReification[PaintableTool[B]] {
      def kind(self: PaintableTool[B]): MamlKind = MamlKind.Image

      def tmsReification(self: PaintableTool[B], buffer: Int)(
          implicit contextShfit: ContextShift[IO]
      ): (Int, Int, Int) => IO[Literal] = (z: Int, x: Int, y: Int) => {
        val extent = BacksplashImage.tmsLevels(z).mapTransform.keyToExtent(x, y)
        val layerEval = LayerTms.apply(IO.pure(self.expr),
                                       IO.pure(self.paramMap),
                                       self.interpreter)
        layerEval(z, x, y) map {
          case Valid(tile) => RasterLit(Raster(self.painter(tile), extent))
          case Invalid(e)  => throw new Exception(s"Unresolvable tile: $e")
        }
      }
    }

  implicit def paintableToolExtentReification[B: ExtentReification] =
    new ExtentReification[PaintableTool[B]] {
      def kind(self: PaintableTool[B]): MamlKind = MamlKind.Image
      def extentReification(self: PaintableTool[B])(
          implicit contextShift: ContextShift[IO]) = {
        val layerEval = LayerExtent.apply(IO.pure(self.expr),
                                          IO.pure(self.paramMap),
                                          self.interpreter)
        (extent: Extent, cellSize: CellSize) =>
          layerEval(extent, cellSize) map {
            case Valid(tile) =>
              if (self.paint) {
                RasterLit(Raster(self.painter(tile), extent))
              } else {
                RasterLit(Raster(tile, extent))
              }
            case Invalid(e) => throw new Exception(s"Unresolvable extent: $e")
          }
      }
    }

  implicit def paintableToolHasRasterExtents[B: HasRasterExtents] =
    new HasRasterExtents[PaintableTool[B]] {
      def rasterExtents(self: PaintableTool[B])(
          implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] = {
        (self.paramMap mapValues { param =>
          param.rasterExtents
        } reduce { (kv1, kv2) =>
          {
            val (_, extentsIO1) = kv1
            val (_, extentsIO2) = kv2
            ("ignored",
             Applicative[IO].map2(extentsIO1, extentsIO2)((nel1, nel2) =>
               nel1 concatNel nel2))
          }
        })._2
      }
    }

  implicit val mosaicTmsReification = new TmsReification[BacksplashMosaic] {
    def kind(self: BacksplashMosaic): MamlKind = MamlKind.Image

    def tmsReification(self: BacksplashMosaic, buffer: Int)(
        implicit contextShift: ContextShift[IO])
      : (Int, Int, Int) => IO[Literal] =
      (z: Int, x: Int, y: Int) =>
        for {
          bandCount <- self
            .take(1)
            .map(_.subsetBands.length)
            .compile
            .fold(0)(_ + _)
          extent = BacksplashImage.tmsLevels(z).mapTransform.keyToExtent(x, y)
          ndtile: MultibandTile = ArrayMultibandTile.empty(
            IntConstantNoDataCellType,
            bandCount,
            256,
            256
          )
          mosaic <- BacksplashMosaic
            .filterRelevant(self)
            .map(_.read(z, x, y))
            .collect({ case Some(mbtile) => Raster(mbtile, extent) })
            .compile
            .fold(Raster(ndtile, extent))({ (mbr1, mbr2) =>
              mbr1.merge(mbr2, NearestNeighbor)
            })
        } yield RasterLit(mosaic)
  }

  implicit val mosaicExtentReification: ExtentReification[BacksplashMosaic] =
    new ExtentReification[BacksplashMosaic] {
      def kind(self: BacksplashMosaic): MamlKind = MamlKind.Image

      def extentReification(self: BacksplashMosaic)(
          implicit contextShift: ContextShift[IO]) =
        (extent: Extent, cs: CellSize) =>
          for {
            bands <- self
              .take(1)
              .map(_.subsetBands)
              .compile
              .toList
              .map(_.flatten)
            ndtile: MultibandTile = ArrayMultibandTile.empty(
              IntConstantNoDataCellType,
              bands.length,
              256,
              256
            )
            mosaic <- BacksplashMosaic
              .filterRelevant(self)
              .map(_.read(extent, cs))
              .collect({ case Some(mbtile) => Raster(mbtile, extent) })
              .compile
              .fold(Raster(ndtile, extent))({ (mbr1, mbr2) =>
                mbr1.merge(mbr2, NearestNeighbor)
              })
          } yield RasterLit(mosaic)
    }

  implicit val mosaicHasRasterExtents: HasRasterExtents[BacksplashMosaic] =
    new HasRasterExtents[BacksplashMosaic] {
      def rasterExtents(self: BacksplashMosaic)(
          implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] = {
        BacksplashMosaic
          .filterRelevant(self)
          .flatMap({ img =>
            fs2.Stream.eval(BacksplashImage.getRasterExtents(img.uri))
          })
          .compile
          .toList
          .map(_.reduceLeft({
            (nel1: NEL[RasterExtent], nel2: NEL[RasterExtent]) =>
              nel1 concatNel nel2
          }))
      }
    }
}

object Implicits extends Implicits
