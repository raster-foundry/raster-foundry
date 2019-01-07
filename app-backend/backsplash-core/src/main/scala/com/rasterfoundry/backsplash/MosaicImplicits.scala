package com.rasterfoundry.backsplash

import com.rasterfoundry.backsplash.color._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.HistogramStore.ToHistogramStoreOps
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.histogram._
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
import scalacache._
import scalacache.caffeine._
import scalacache.memoization._
import scalacache.CatsEffect.modes._
import ProjectStore._
import ToolStore._
import ExtentReification._
import HasRasterExtents._
import TmsReification._
import com.typesafe.scalalogging.LazyLogging

class MosaicImplicits[HistStore: HistogramStore](mtr: MetricsRegistrator,
                                                 histStore: HistStore)
    extends ToTmsReificationOps
    with ToExtentReificationOps
    with ToHasRasterExtentsOps
    with ToHistogramStoreOps
    with ToProjectStoreOps
    with ToToolStoreOps
    with LazyLogging {

  implicit val histCache = Cache.histCache
  implicit val flags = Cache.histCacheFlags

  val readMosaicTimer =
    mtr.newTimer(classOf[MosaicImplicits[HistStore]], "read-mosaic")
  val readSceneTmsTimer =
    mtr.newTimer(classOf[MosaicImplicits[HistStore]], "read-tms-scene")
  val readSceneExtentTimer =
    mtr.newTimer(classOf[MosaicImplicits[HistStore]], "read-extent-scene")
  val readSceneHistTimer =
    mtr.newTimer(classOf[MosaicImplicits[HistStore]], "read-scene-hist")
  val mbColorSceneTimer =
    mtr.newTimer(classOf[MosaicImplicits[HistStore]], "color-multiband-scene")

  implicit val mosaicTmsReification = new TmsReification[BacksplashMosaic] {
    def kind(self: BacksplashMosaic): MamlKind = MamlKind.Image

    def getNoDataValue(cellType: CellType): Option[Double] = {
      cellType match {
        case ByteUserDefinedNoDataCellType(value)   => Some(value.toDouble)
        case UByteUserDefinedNoDataCellType(value)  => Some(value.toDouble)
        case UByteConstantNoDataCellType            => Some(0)
        case ShortUserDefinedNoDataCellType(value)  => Some(value.toDouble)
        case UShortUserDefinedNoDataCellType(value) => Some(value.toDouble)
        case UShortConstantNoDataCellType           => Some(0)
        case IntUserDefinedNoDataCellType(value)    => Some(value.toDouble)
        case FloatUserDefinedNoDataCellType(value)  => Some(value.toDouble)
        case DoubleUserDefinedNoDataCellType(value) => Some(value.toDouble)
        case _: NoNoData                            => None
        case _: ConstantNoData[_]                   => Some(Double.NaN)
      }
    }

    def mergeTiles(
        tiles: IO[List[MultibandTile]]): IO[Option[MultibandTile]] = {
      tiles.map(_.reduceOption((_) merge (_)))
    }

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
            val ioMBT = filtered
              .flatMap({ relevant =>
                fs2.Stream.eval {
                  for {
                    imFiber <- (IO {
                      val time = readSceneTmsTimer.time()
                      logger.debug(
                        s"Reading Tile ${relevant.imageId} ${z}-${x}-${y}")
                      val img = relevant.read(z, x, y)
                      time.stop()
                      img
                    }).start
                    histsFiber <- ({
                      val time = readSceneHistTimer.time()
                      logger.debug(
                        s"Reading Histogram ${relevant.imageId} ${z}-${x}-${y}")
                      val hists = getHistogramWithCache(relevant)
                      time.stop()
                      hists
                    }).start
                    im <- imFiber.join
                    hists <- histsFiber.join
                  } yield {
                    im map {
                      mbTile =>
                        val time = mbColorSceneTimer.time()
                        val noDataValue = getNoDataValue(mbTile.cellType)
                        logger.info(
                          s"NODATA Value: ${noDataValue} with CellType: ${mbTile.cellType}")
                        val colored =
                          relevant.corrections.colorCorrect(mbTile,
                                                            hists,
                                                            noDataValue)
                        time.stop()
                        colored
                    }
                  }
                }
              })
              .collect({ case Some(mbTile) => mbTile })
              .compile
              .toList
            mergeTiles(ioMBT).map {
              case Some(t) => Raster(t, extent)
              case _       => Raster(ndtile, extent)
            }
          } else {
            // Assume that we're in a single band case. It isn't obvious what it would
            // mean if the band count weren't 3 or 1, so just make the assumption that we
            // wouldn't do that to ourselves and don't handle the remainder
            BacksplashMosaic.getStoreHistogram(filtered, histStore) flatMap {
              hists =>
                {
                  val ioMBT = (BacksplashMosaic.filterRelevant(self) map {
                    relevant =>
                      val time = readSceneTmsTimer.time()
                      val img = relevant.read(z, x, y) map { im =>
                        ColorRampMosaic.colorTile(
                          im,
                          hists,
                          relevant.singleBandOptions getOrElse {
                            throw SingleBandOptionsException(
                              "Must specify single band options when requesting single band visualization.")
                          }
                        )
                      }
                      time.stop()
                      img
                  }).collect({ case Some(mbtile) => mbtile }).compile.toList
                  mergeTiles(ioMBT).map {
                    case Some(t) => Raster(t, extent)
                    case _       => Raster(ndtile, extent)
                  }
                }
            }
          }
          timedMosaic <- mtr.timedIO(mosaic, readMosaicTimer)
        } yield {
          RasterLit(timedMosaic)
      }
  }

  /** Private histogram retrieval method to allow for caching on/off via settings
    *
    * @param relevant
    * @return
    */
  private def getHistogramWithCache(
      relevant: BacksplashImage
  )(implicit @cacheKeyExclude flags: Flags): IO[Array[Histogram[Double]]] =
    memoizeF(None) {
      logger.debug(s"Retrieving Histograms for ${relevant.imageId} from Source")
      histStore.layerHistogram(relevant.imageId, relevant.subsetBands)
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
            mosaic = if (bands.length == 3) {
              filtered
                .flatMap({ relevant =>
                  fs2.Stream.eval {
                    for {
                      imFiber <- (
                        IO {
                          val time = readSceneTmsTimer.time()
                          val img = relevant.read(extent, cs)
                          time.stop()
                          img
                        }
                      ).start
                      histsFiber <- ({
                        val time = readSceneHistTimer.time()
                        val hists =
                          histStore.layerHistogram(relevant.imageId,
                                                   relevant.subsetBands)
                        time.stop()
                        hists
                      }).start
                      im <- imFiber.join
                      hists <- histsFiber.join
                    } yield {
                      im map { mbTile =>
                        val time = mbColorSceneTimer.time()
                        val colored =
                          relevant.corrections.colorCorrect(mbTile, hists, None)
                        time.stop()
                        colored
                      }
                    }
                  }
                })
                .collect({ case Some(mbTile) => mbTile })
                .compile
                .fold(ndtile)({ (mbr1, mbr2) =>
                  mbr1 merge mbr2
                })
                .map(Raster(_, extent))
            } else {
              BacksplashMosaic.getStoreHistogram(filtered, histStore) map {
                layerHist =>
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
              .collect({ case Some(mbTile) => mbTile })
              .compile
              .fold(ndtile)({ (mbr1, mbr2) =>
                mbr1 merge mbr2
              })
              .map(Raster(_, extent))
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
