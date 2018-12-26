package com.rasterfoundry.backsplash

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.proj4.{CRS, WebMercator, LatLng}
import geotrellis.spark.SpatialKey
import geotrellis.server._
import geotrellis.contrib.vlm._
import geotrellis.contrib.vlm.geotiff._

import com.azavea.maml.ast._
import com.azavea.maml.eval._

import cats._
import cats.implicits._
import cats.data.{NonEmptyList => NEL}
import cats.effect._

import TmsReification._
import ExtentReification._
import HasRasterExtents._

trait GtImplicits
    extends ToTmsReificationOps
    with ToExtentReificationOps
    with ToHasRasterExtentsOps {

  def readWithGt(img: BacksplashImage,
                 z: Int,
                 x: Int,
                 y: Int): Option[MultibandTile] = {
    val rs = new GeoTiffRasterSource(img.uri)
    val layoutDefinition = BacksplashImage.tmsLevels(z)
    rs.reproject(WebMercator)
      .tileToLayout(layoutDefinition, NearestNeighbor)
      .read(SpatialKey(x, y), img.subsetBands)
  }
  def readWithGt(img: BacksplashImage,
                 extent: Extent,
                 cs: CellSize): Option[MultibandTile] = {
    val rs = new GeoTiffRasterSource(img.uri)
    val destinationExtent = extent.reproject(LatLng, WebMercator)
    rs.reproject(WebMercator)
      .resampleToGrid(RasterExtent(extent, cs), NearestNeighbor)
      .read(destinationExtent, img.subsetBands.toSeq)
      .map(_.tile)
  }

  implicit val mosaicTmsReification = new TmsReification[BacksplashMosaic] {
    def kind(self: BacksplashMosaic): MamlKind = MamlKind.Image

    def tmsReification(self: BacksplashMosaic, buffer: Int)(
        implicit contextShift: ContextShift[IO])
      : (Int, Int, Int) => IO[Literal] =
      (z: Int, x: Int, y: Int) => {
        val extent = BacksplashImage.tmsLevels(z).mapTransform.keyToExtent(x, y)
        val bandCount = self
          .take(1)
          .map(_.subsetBands.length)
          .compile
          .fold(0)(_ + _)
          .unsafeRunSync
        val ndtile: MultibandTile = ArrayMultibandTile.empty(
          IntConstantNoDataCellType,
          bandCount,
          256,
          256)
        BacksplashMosaic
          .filterRelevant(self)
          .map(readWithGt(_, z, x, y))
          .map({ maybeMBTile =>
            val mbtile = maybeMBTile.getOrElse {
              throw new Exception(
                s"really expected to find a tile at $z, $x, $y for $self")
            }
            Raster(mbtile, extent)
          })
          .compile
          .fold(Raster(ndtile, extent))({ (mbr1, mbr2) =>
            mbr1.merge(mbr2, NearestNeighbor)
          })
          .map(RasterLit(_))
      }
  }

  implicit val mosaicExtentReification: ExtentReification[BacksplashMosaic] =
    new ExtentReification[BacksplashMosaic] {
      def kind(self: BacksplashMosaic): MamlKind = MamlKind.Image

      def extentReification(self: BacksplashMosaic)(
          implicit contextShift: ContextShift[IO]) =
        (extent: Extent, cs: CellSize) => {
          val bands = self
            .take(1)
            .map(_.subsetBands)
            .compile
            .toList
            .map(_.flatten)
            .unsafeRunSync
          val ndtile: MultibandTile = ArrayMultibandTile.empty(
            IntConstantNoDataCellType,
            bands.length,
            256,
            256)
          BacksplashMosaic
            .filterRelevant(self)
            .map(readWithGt(_, extent, cs))
            .map({ maybeMBTile =>
              val mbtile = maybeMBTile.getOrElse {
                throw new Exception(
                  s"really expected to find a tile for $extent and $cs for $self")
              }
              Raster(mbtile, extent)
            })
            .compile
            .fold(Raster(ndtile, extent))({ (mbr1, mbr2) =>
              mbr1.merge(mbr2, NearestNeighbor)
            })
            .map(RasterLit(_))
        }
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

      def crs(self: BacksplashMosaic)(
          implicit contextShift: ContextShift[IO]): IO[CRS] = ???
    }
}

object GtImplicits extends GtImplicits
