package com.rasterfoundry.backsplash

import com.rasterfoundry.backsplash.color._
import com.rasterfoundry.backsplash.error._

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

class MosaicImplicits(mtr: MetricsRegistrator)
    extends ToTmsReificationOps
    with ToExtentReificationOps
    with ToHasRasterExtentsOps
    with ToProjectStoreOps
    with ToToolStoreOps {

  val readMosaicTimer =
    mtr.newTimer(classOf[MosaicImplicits], "read-mosaic")
  val readSceneTmsTimer =
    mtr.newTimer(classOf[MosaicImplicits], "read-tms-scene")
  val readSceneExtentTimer =
    mtr.newTimer(classOf[MosaicImplicits], "read-extent-scene")

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
            .toList
            .map(_.fold(0)(_ + _))
          filtered = BacksplashMosaic.filterRelevant(self)
          // This is a stop gap -- we know we don't want to fetch the histogram from the raw data
          // all the time, and that actually it lives in the database or a cache or whatever. A
          // HistogramStore[Param] would solve this problem, but we're putting that off because there
          // are other things to do to get back to parity to start the work we actually care about,
          // which is making things fast (and this will be a layup for "hmm I wonder what could be faster")
          layerHist <- BacksplashMosaic.layerHistogram(self) map {
            case Valid(hists) => hists
            case Invalid(e) =>
              throw MetadataException(s"Could not resolve histograms: $e")
          }
          _ <- IO { println("Made it past getting histograms") }
          extent = BacksplashImage.tmsLevels(z).mapTransform.keyToExtent(x, y)
          // for single band imagery, after color correction we have RGBA, so
          // the empty tile needs to be four band as well
          ndtile: MultibandTile = ArrayMultibandTile.empty(
            IntConstantNoDataCellType,
            if (bandCount == 1) 4 else bandCount,
            256,
            256
          )
          mosaic = if (bandCount == 3) {
            filtered
              .map({ relevant =>
                val time = readSceneTmsTimer.time()
                val img = relevant.colorCorrect(z, x, y, layerHist, None)
                time.stop()
                img
              })
              .collect({ case Some(mbtile) => Raster(mbtile, extent) })
              .compile
              .fold(Raster(ndtile, extent))({ (mbr1, mbr2) =>
                mbr1.merge(mbr2, NearestNeighbor)
              })
          } else {
            // Assume that we're in a single band case. It isn't obvious what it would
            // mean if the band count weren't 3 or 1, so just make the assumption that we
            // wouldn't do that to ourselves and don't handle the remainder
            BacksplashMosaic
              .filterRelevant(self)
              .map({ relevant =>
                val time = readSceneTmsTimer.time()
                val img = relevant.read(z, x, y) map {
                  ColorRampMosaic.colorTile(
                    _,
                    layerHist,
                    relevant.singleBandOptions getOrElse {
                      throw SingleBandOptionsException(
                        "Must specify single band options when requesting single band visualization.")
                    }
                  )
                }
                time.stop()
                img
              })
              .collect({ case Some(mbtile) => Raster(mbtile, extent) })
              .compile
              .fold(Raster(ndtile, extent))({ (mbr1, mbr2) =>
                mbr1.merge(mbr2, NearestNeighbor)
              })
          }
          timedMosaic <- mtr.timedIO(mosaic, readMosaicTimer)
        } yield {
          RasterLit(timedMosaic)
      }
  }

  // We need to be able to pass information about whether scenes should paint themselves while
  // we're working through the stream from the route into the extent reification. The solution
  // we (Nathan Zimmerman and I) came up with that is the least painful is two implicits, choosing
  // which one to use based on the information we have in the route. That solution can go away if:
  //
  // 1. color correction becomes a local op in MAML and we don't have to care about it in RF anymore
  // 2. all color correction options (single and multiband) move onto backsplash images and
  //    BacksplashImage gets a paint attribute telling it whether to apply color correction
  //
  // Neither of those changes really fits in the scope of backsplash performance work, so we're doing
  // this for now instead.
  val paintedMosaicExtentReification: ExtentReification[BacksplashMosaic] =
    new ExtentReification[BacksplashMosaic] {
      def kind(self: BacksplashMosaic): MamlKind = MamlKind.Image

      def extentReification(self: BacksplashMosaic)(
          implicit contextShift: ContextShift[IO]) =
        (extent: Extent, cs: CellSize) => {
          val filtered = BacksplashMosaic.filterRelevant(self)
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
            layerHist <- BacksplashMosaic.layerHistogram(filtered) map {
              case Valid(hists) => hists
              case Invalid(e) =>
                throw MetadataException(s"Could not resolve histograms: $e")
            }
            mosaic = if (bands.length == 3) {
              filtered
                .map({ relevant =>
                  val time = readSceneExtentTimer.time()
                  val img = relevant.colorCorrect(extent, cs, layerHist, None)
                  time.stop()
                  img
                })
                .collect({ case Some(mbtile) => Raster(mbtile, extent) })
                .compile
                .fold(Raster(ndtile, extent))({ (mbr1, mbr2) =>
                  mbr1.merge(mbr2, NearestNeighbor)
                })
            } else {
              filtered
                .map({ relevant =>
                  val time = readSceneExtentTimer.time()
                  val img = relevant.read(extent, cs) map {
                    ColorRampMosaic
                      .colorTile(
                        _,
                        layerHist,
                        relevant.singleBandOptions getOrElse {
                          throw SingleBandOptionsException(
                            "Must specify single band options when requesting single band visualization.")
                        }
                      )
                  }
                  time.stop()
                  img
                })
                .collect({ case Some(mbtile) => Raster(mbtile, extent) })
                .compile
                .fold(Raster(ndtile, extent))({ (mbr1, mbr2) =>
                  mbr1.merge(mbr2, NearestNeighbor)
                })
            }
            timedMosaic <- mtr.timedIO(mosaic, readMosaicTimer)
          } yield RasterLit(timedMosaic)
        }
    }

  implicit val rawMosaicExtentReification: ExtentReification[BacksplashMosaic] =
    new ExtentReification[BacksplashMosaic] {
      def kind(self: BacksplashMosaic): MamlKind = MamlKind.Image

      def extentReification(self: BacksplashMosaic)(
          implicit contextShift: ContextShift[IO]) =
        (extent: Extent, cs: CellSize) => {
          for {
            bands <- self
              .take(1)
              .map(_.subsetBands.length)
              .compile
              .fold(0)(_ + _)
            ndtile: MultibandTile = ArrayMultibandTile.empty(
              IntConstantNoDataCellType,
              bands,
              256,
              256
            )
            mosaic = BacksplashMosaic
              .filterRelevant(self)
              .map({ relevant =>
                val time = readSceneExtentTimer.time()
                val img = relevant.read(extent, cs)
                time.stop()
                img
              })
              .collect({ case Some(mbtile) => Raster(mbtile, extent) })
              .compile
              .fold(Raster(ndtile, extent))({ (mbr1, mbr2) =>
                mbr1.merge(mbr2, NearestNeighbor)
              })
            timedMosaic <- mtr.timedIO(mosaic, readMosaicTimer)
          } yield RasterLit(timedMosaic)
        }
    }

  implicit val mosaicHasRasterExtents: HasRasterExtents[BacksplashMosaic] =
    new HasRasterExtents[BacksplashMosaic] {
      def rasterExtents(self: BacksplashMosaic)(
          implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] = {
        val mosaic = BacksplashMosaic
          .filterRelevant(self)
          .flatMap({ img =>
            fs2.Stream.eval(BacksplashImage.getRasterExtents(img.uri))
          })
          .compile
          .toList
          .map(_.reduceLeft({
            (nel1: NEL[RasterExtent], nel2: NEL[RasterExtent]) =>
              val updatedExtent = nel1.head.extent combine nel2.head.extent
              val updated1 = nel1.map { re =>
                RasterExtent(updatedExtent,
                             CellSize(re.cellwidth, re.cellheight))
              }
              val updated2 = nel2.map { re =>
                RasterExtent(updatedExtent,
                             CellSize(re.cellwidth, re.cellheight))
              }
              updated1 concatNel updated2
          }))
        mtr.timedIO(mosaic, readMosaicTimer)
      }
    }
}
