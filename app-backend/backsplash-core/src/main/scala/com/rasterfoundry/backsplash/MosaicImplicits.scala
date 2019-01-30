package com.rasterfoundry.backsplash

import com.rasterfoundry.backsplash.color._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.HistogramStore.ToHistogramStoreOps

import geotrellis.proj4.WebMercator
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.reproject._
import geotrellis.server._
import com.azavea.maml.ast._
import cats.implicits._
import cats.data.{NonEmptyList => NEL}
import cats.effect._
import scalacache._
import scalacache.memoization._
import scalacache.CatsEffect.modes._
import ProjectStore._
import ToolStore._
import ExtentReification._
import HasRasterExtents._
import TmsReification._
import com.typesafe.scalalogging.LazyLogging

import java.util.UUID

class MosaicImplicits[HistStore: HistogramStore](histStore: HistStore)
    extends ToTmsReificationOps
    with ToExtentReificationOps
    with ToHasRasterExtentsOps
    with ToHistogramStoreOps
    with ToProjectStoreOps
    with ToToolStoreOps
    with LazyLogging {

  implicit val histCache = Cache.histCache
  implicit val flags = Cache.histCacheFlags

  val streamConcurrency = Config.parallelism.streamConcurrency

  // To be used when folding over/merging tiles
  val invisiCellType = IntUserDefinedNoDataCellType(0)
  val invisiTile = IntUserDefinedNoDataArrayTile(Array.fill(65536)(0),
                                                 256,
                                                 256,
                                                 invisiCellType)

  def mergeTiles(tiles: IO[List[MultibandTile]]): IO[Option[MultibandTile]] = {
    tiles.map(_.reduceOption((t1: MultibandTile, t2: MultibandTile) => {
      logger.trace(s"Tile 1 size: ${t1.band(0).cols}, ${t1.band(0).rows}")
      logger.trace(s"Tile 2 size: ${t2.band(0).cols}, ${t2.band(0).rows}")
      t1 merge t2
    }))
  }

  val rawMosaicTmsReification = new TmsReification[BacksplashMosaic] {
    def kind(self: BacksplashMosaic): MamlKind = MamlKind.Image

    def tmsReification(self: BacksplashMosaic, buffer: Int)(
        implicit contextShift: ContextShift[IO]
    ): (Int, Int, Int) => IO[Literal] = (z: Int, x: Int, y: Int) => {
      val extent = BacksplashImage.tmsLevels(z).mapTransform.keyToExtent(x, y)
      val mosaic = {
        val mbtIO = (BacksplashMosaic.filterRelevant(self) map { relevant =>
          logger.debug(s"Band Subset Required: ${relevant.subsetBands}")
          relevant.read(z, x, y)
        }).collect({ case Some(mbtile) => mbtile }).compile.toList
        mbtIO.map(_.reduceOption(_ merge _) match {
          case Some(t) => Raster(t, extent)
          case _       => Raster(MultibandTile(invisiTile), extent)
        })
      }
      mosaic.map(RasterLit(_))
    }
  }

  val paintedMosaicTmsReification = new TmsReification[BacksplashMosaic] {
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

    /** We know the head below is safe because we have to have images to get there */
    @SuppressWarnings(Array("TraversableHead"))
    def tmsReification(self: BacksplashMosaic, buffer: Int)(
        implicit contextShift: ContextShift[IO])
      : (Int, Int, Int) => IO[Literal] =
      (z: Int, x: Int, y: Int) =>
        (for {
          bandCount <- self
            .take(1)
            .map(_.subsetBands.length)
            .compile
            .toList
            .map(_.fold(0)(_ + _))
          _ <- {
            if (bandCount == 0) {
              IO.raiseError(NoDataInRegionException())
            } else IO.unit
          }
          filtered = BacksplashMosaic.filterRelevant(self)
          extent = BacksplashImage.tmsLevels(z).mapTransform.keyToExtent(x, y)
          // for single band imagery, after color correction we have RGBA, so
          // the empty tile needs to be four band as well
          mosaic <- if (bandCount == 3) {
            val ioMBT = filtered
              .parEvalMap(streamConcurrency)({ relevant =>
                for {
                  imFiber <- IO {
                    logger.debug(
                      s"Reading Tile ${relevant.imageId} ${z}-${x}-${y}")
                    relevant.read(z, x, y)
                  }.start
                  histsFiber <- {
                    logger.debug(
                      s"Reading Histogram ${relevant.imageId} ${z}-${x}-${y}")
                    getHistogramWithCache(relevant)
                  }.start
                  hists <- histsFiber.join
                  im <- imFiber.join
                  renderedTile <- IO.pure {
                    im map { mbTile =>
                      val noDataValue = getNoDataValue(mbTile.cellType)
                      logger.debug(
                        s"NODATA Value: ${noDataValue} with CellType: ${mbTile.cellType}"
                      )
                      relevant.corrections.colorCorrect(mbTile,
                                                        hists,
                                                        noDataValue)
                    }
                  }
                } yield {
                  renderedTile
                }
              })
              .collect({ case Some(tile) => tile })
              .compile
              .toList
            mergeTiles(ioMBT).map {
              case Some(t) => Raster(t, extent)
              case _       => Raster(MultibandTile(invisiTile), extent)
            }
          } else {
            // Assume that we're in a single band case. It isn't obvious what it would
            // mean if the band count weren't 3 or 1, so just make the assumption that we
            // wouldn't do that to ourselves and don't handle the remainder
            val ioMBTwithSBO = BacksplashMosaic
              .filterRelevant(self)
              .parEvalMap(streamConcurrency) { relevant =>
                logger.debug(s"Band Subset Required: ${relevant.subsetBands}")
                IO { (relevant.read(z, x, y), relevant.singleBandOptions) }
              }
              .collect({ case (Some(mbtile), Some(sbo)) => (mbtile, sbo) })
              .compile
              .toList

            for {
              firstImOption <- BacksplashMosaic.first(
                BacksplashMosaic.filterRelevant(self))
              histograms <- {
                histStore.projectLayerHistogram(
                  firstImOption map {
                    _.projectLayerId
                  } getOrElse {
                    throw MetadataException(
                      "Cannot produce tiles from empty mosaics")
                  },
                  List(firstImOption flatMap {
                    _.singleBandOptions map { _.band }
                  } getOrElse {
                    throw SingleBandOptionsException(
                      "Must provide band for single band visualization")
                  })
                )
              }
              multibandTilewithSBO <- ioMBTwithSBO
            } yield {
              val (tiles, sbos) = multibandTilewithSBO.unzip
              logger.debug(s"Length of Histograms: ${histograms.length}")
              val combinedHistogram = histograms.reduce(_ merge _)
              tiles match {
                case tile :: Nil =>
                  Raster(
                    ColorRampMosaic.colorTile(tile.interpretAs(invisiCellType),
                                              List(combinedHistogram),
                                              sbos.head),
                    extent)
                case someTiles =>
                  someTiles.reduceOption(
                    _.interpretAs(invisiCellType) merge _.interpretAs(
                      invisiCellType)) match {
                    case Some(t) =>
                      Raster(
                        ColorRampMosaic.colorTile(
                          t,
                          List(combinedHistogram),
                          sbos.head
                        ),
                        extent
                      )
                    case _ => Raster(MultibandTile(invisiTile), extent)
                  }

              }
            }
          }
        } yield {
          RasterLit(mosaic)
        }).attempt.map {
          case Left(NoDataInRegionException()) =>
            RasterLit(
              Raster(MultibandTile(invisiTile, invisiTile, invisiTile),
                     Extent(0, 0, 256, 256)))
          case Left(e)          => throw e
          case Right(rasterLit) => rasterLit
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
          for {
            bands <- self
              .take(1)
              .map(_.subsetBands)
              .compile
              .toList
              .map(_.flatten)
            mosaic <- if (bands.length == 3) {
              BacksplashMosaic
                .filterRelevant(self)
                .parEvalMap(streamConcurrency)({ relevant =>
                  {
                    for {
                      imFiber <- IO {
                        val img = relevant.read(extent, cs)
                        img
                      }.start
                      histsFiber <- {
                        histStore.layerHistogram(relevant.imageId,
                                                 relevant.subsetBands)
                      }.start
                      im <- imFiber.join
                      hists <- histsFiber.join
                      renderedTile <- IO.pure {
                        im map { mbTile =>
                          logger.debug(
                            s"N bands in resulting tile: ${mbTile.bands.length}")
                          relevant.corrections.colorCorrect(mbTile, hists, None)
                        }
                      }
                    } yield {
                      renderedTile
                    }
                  }
                })
                .collect({
                  case Some(mbTile) =>
                    Raster(mbTile.interpretAs(invisiCellType), extent)
                })
                .compile
                .toList
                .map(_.reduceOption(_ merge _))
                .map({
                  case Some(r) => r
                  case _       => Raster(invisiTile, extent)
                })
            } else {
              logger.debug("Creating single band extent")
              BacksplashMosaic.getStoreHistogram(
                BacksplashMosaic.filterRelevant(self),
                histStore) flatMap { layerHist =>
                {
                  logger.debug(s"Got a layer hist: ${layerHist.length}")
                  BacksplashMosaic
                    .filterRelevant(self)
                    .parEvalMap(streamConcurrency)({ relevant =>
                      IO {
                        relevant.read(extent, cs) map {
                          ColorRampMosaic
                            .colorTile(
                              _,
                              layerHist,
                              relevant.singleBandOptions getOrElse {
                                throw SingleBandOptionsException(
                                  "Must specify single band options when requesting single band visualization."
                                )
                              }
                            )
                        }
                      }
                    })
                    .collect({
                      case Some(mbtile) => Raster(mbtile, extent)
                    })
                    .compile
                    .toList
                    .map(_.reduceOption(_ merge _))
                    .map({
                      case Some(r) => r
                      case _       => Raster(invisiTile, extent)
                    })
                }
              }
            }
          } yield RasterLit(mosaic)
        }
    }

  implicit val rawMosaicExtentReification: ExtentReification[BacksplashMosaic] =
    new ExtentReification[BacksplashMosaic] {
      def kind(self: BacksplashMosaic): MamlKind = MamlKind.Image

      def extentReification(self: BacksplashMosaic)(
          implicit contextShift: ContextShift[IO]) =
        (extent: Extent, cs: CellSize) => {
          val mosaic = BacksplashMosaic
            .filterRelevant(self)
            .parEvalMap(streamConcurrency) { relevant =>
              IO {
                relevant.read(extent, cs)
              }
            }
            .collect({ case Some(mbTile) => mbTile })
            .compile
            .toList
            .map { tiles =>
              val rasters = tiles.map(Raster(_, extent))
              rasters.reduceOption(_ merge _) match {
                case Some(r) => RasterLit(r)
                case _       => RasterLit(Raster(MultibandTile(invisiTile), extent))
              }
            }
          mosaic
        }
    }

  implicit val mosaicHasRasterExtents: HasRasterExtents[BacksplashMosaic] =
    new HasRasterExtents[BacksplashMosaic] {
      def rasterExtents(self: BacksplashMosaic)(
          implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] = {
        val mosaic = BacksplashMosaic
          .filterRelevant(self)
          .flatMap({ img =>
            {
              fs2.Stream.eval(BacksplashImage.getRasterExtents(img.uri) map {
                extents =>
                  extents map {
                    ReprojectRasterExtent(_, img.rasterSource.crs, WebMercator)
                  }
              })
            }
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
        mosaic
      }
    }
}
