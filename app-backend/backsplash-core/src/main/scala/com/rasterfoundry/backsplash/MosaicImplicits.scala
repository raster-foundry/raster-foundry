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
import cats.implicits._
import cats.data.{NonEmptyList => NEL}
import cats.effect._
import cats.Semigroup
import scalacache._
import scalacache.memoization._
import scalacache.CatsEffect.modes._
import RenderableStore._
import ToolStore._
import ExtentReification._
import HasRasterExtents._
import TmsReification._
import com.colisweb.tracing.TracingContext
import com.typesafe.scalalogging.LazyLogging
import com.rasterfoundry.datamodel.SingleBandOptions

class MosaicImplicits[HistStore: HistogramStore, RendStore: RenderableStore](
    histStore: HistStore,
    rendStore: RendStore
) extends ToTmsReificationOps
    with ToExtentReificationOps
    with ToHasRasterExtentsOps
    with ToHistogramStoreOps
    with ToRenderableStoreOps
    with ToToolStoreOps
    with LazyLogging {

  implicit val histCache = Cache.histCache
  implicit val flags = Cache.histCacheFlags

  val streamConcurrency = Config.parallelism.streamConcurrency

  // To be used when folding over/merging tiles
  val invisiCellType = IntUserDefinedNoDataCellType(0)
  val invisiTile = IntUserDefinedNoDataArrayTile(
    Array.fill(65536)(0),
    256,
    256,
    invisiCellType
  )

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

  def mergeTiles(tiles: IO[List[MultibandTile]]): IO[Option[MultibandTile]] = {
    tiles.map(_.reduceOption((t1: MultibandTile, t2: MultibandTile) => {
      logger.trace(s"Tile 1 size: ${t1.band(0).cols}, ${t1.band(0).rows}")
      logger.trace(s"Tile 2 size: ${t2.band(0).cols}, ${t2.band(0).rows}")
      t1 merge t2
    }))
  }

  def chooseMosaic(
      z: Int,
      baseImage: BacksplashImage[IO],
      config: OverviewConfig,
      fallbackIms: NEL[BacksplashImage[IO]]): NEL[BacksplashImage[IO]] =
    config match {
      case OverviewConfig(Some(overviewLocation), Some(minZoom))
          if z <= minZoom =>
        NEL.of(
          BacksplashGeotiff(
            baseImage.imageId,
            baseImage.projectId,
            baseImage.projectLayerId,
            overviewLocation,
            baseImage.subsetBands,
            baseImage.corrections,
            baseImage.singleBandOptions,
            baseImage.mask,
            baseImage.footprint,
            baseImage.metadata
          ),
          Nil: _*
        )
      case _ =>
        fallbackIms
    }
  @SuppressWarnings(Array("TraversableHead"))
  def renderMosaicSingleBand(
      mosaic: NEL[BacksplashImage[IO]],
      z: Int,
      x: Int,
      y: Int,
      tracingContext: TracingContext[IO]
  )(implicit contextShift: ContextShift[IO]): IO[Raster[MultibandTile]] = {
    type MBTTriple = (MultibandTile, SingleBandOptions.Params, Option[Double])
    val extent = BacksplashImage.tmsLevels(z).mapTransform.keyToExtent(x, y)
    val ioMBTwithSBO: IO[List[MBTTriple]] = tracingContext.childSpan(
      "renderMosaicSingleBand") use { context =>
      mosaic
        .parTraverse((relevant: BacksplashImage[IO]) => {
          logger.debug(s"Band Subset Required: ${relevant.subsetBands}")
          relevant.read(z, x, y, context) map {
            (_, relevant.singleBandOptions, relevant.metadata.noDataValue)
          }
        })
        .map(nel =>
          nel.collect({
            case (Some(mbtile), Some(sbo), nd) => (mbtile, sbo, nd)
          }))
    }
    for {
      imagesNel <- ioMBTwithSBO map { _.toNel } flatMap {
        case Some(ims) => IO.pure(ims)
        case None      => IO.raiseError(NoScenesException)
      }
      firstIm = mosaic.head
      histograms <- tracingContext.childSpan("renderMosaicSingleBand.histogram") use {
        _ =>
          firstIm.singleBandOptions map { _.band } map { bd =>
            histStore.projectLayerHistogram(firstIm.projectLayerId, List(bd))
          } getOrElse {
            IO.raiseError(
              SingleBandOptionsException(
                "Must provide band for single band visualization"
              )
            )
          }
      }
    } yield {
      val combinedHistogram = histograms.reduce(_ merge _)
      val (_, firstSbos, firstNd) = imagesNel.head
      imagesNel.toList match {
        case (tile, sbo, nd) :: Nil =>
          Raster(ColorRampMosaic.colorTile(interpretAsFallback(tile, nd),
                                           List(combinedHistogram),
                                           sbo),
                 extent)
        case someTiles =>
          val outTile = someTiles.foldLeft(MultibandTile(invisiTile))(
            (baseTile: MultibandTile, triple2: MBTTriple) =>
              interpretAsFallback(baseTile, firstNd) merge interpretAsFallback(
                triple2._1,
                firstNd))
          Raster(
            ColorRampMosaic.colorTile(
              outTile,
              List(combinedHistogram),
              firstSbos
            ),
            extent
          )
      }
    }
  }

  def interpretAsFallback[T1, T2](tile: MultibandTile,
                                  noData: Option[Double]): MultibandTile =
    tile.interpretAs(
      DoubleUserDefinedNoDataCellType(
        noData
          .orElse(getNoDataValue(tile.cellType))
          .getOrElse(0.0)
      )
    )

  @SuppressWarnings(Array("TraversableHead"))
  def renderMosaicMultiband(
      mosaic: NEL[BacksplashImage[IO]],
      z: Int,
      x: Int,
      y: Int,
      tracingContext: TracingContext[IO]
  )(implicit contextShift: ContextShift[IO]): IO[Raster[MultibandTile]] = {
    val extent = BacksplashImage.tmsLevels(z).mapTransform.keyToExtent(x, y)
    val ioMBT = tracingContext.childSpan("renderMosaic") use { context =>
      mosaic
        .parTraverse((relevant: BacksplashImage[IO]) => {
          val tags = Map("sceneId" -> relevant.imageId.toString,
                         "projectId" -> relevant.projectId.toString,
                         "projectLayerId" -> relevant.projectLayerId.toString,
                         "zoom" -> z.toString)

          context
            .childSpan("renderMosaicMultiband.renderBacksplashImage", tags) use {
            childContext =>
              for {
                imFiber <- relevant.read(z, x, y, childContext).start
                histsFiber <- {
                  childContext.childSpan("renderMosaicMultiband.readHistogram",
                                         tags) use { _ =>
                    getHistogramWithCache(relevant, childContext)
                  }
                }.start
                hists <- histsFiber.join
                im <- imFiber.join
                renderedTile <- {
                  childContext.childSpan("renderMosaicMultiband.colorCorrect",
                                         tags) use {
                    _ =>
                      IO.pure {
                        im map { mbTile =>
                          val noDataValue = getNoDataValue(mbTile.cellType)
                          logger.debug(
                            s"NODATA Value: $noDataValue with CellType: ${mbTile.cellType}"
                          )
                          relevant.corrections.colorCorrect(
                            mbTile,
                            hists,
                            relevant.metadata.noDataValue orElse noDataValue orElse Some(
                              0))
                        }
                      }
                  }
                }
              } yield {
                renderedTile
              }
          }
        })
        .map(nel => nel.collect({ case Some(tile) => tile }))
    }
    mergeTiles(ioMBT).map {
      case Some(t) => Raster(t, extent)
      case _ =>
        Raster(MultibandTile(invisiTile, invisiTile, invisiTile), extent)
    }
  }

  val rawMosaicTmsReification: TmsReification[BacksplashMosaic] =
    new TmsReification[BacksplashMosaic] {

      def tmsReification(self: BacksplashMosaic, buffer: Int)(
          implicit contextShift: ContextShift[IO]
      ): (Int, Int, Int) => IO[ProjectedRaster[MultibandTile]] =
        (z: Int, x: Int, y: Int) => {
          val extent =
            BacksplashImage.tmsLevels(z).mapTransform.keyToExtent(x, y)
          val mosaic = {
            val mbtIO = self.flatMap {
              case (tracingContext, listBsi) =>
                tracingContext.childSpan("getMergedRawMosaic") use {
                  childContext =>
                    val listIO = listBsi.parTraverse { bsi =>
                      bsi.read(z, x, y, childContext)
                    }
                    childContext.childSpan("mergeRawTiles") use { _ =>
                      listIO.map(_.flatten.reduceOption(_ merge _))
                    }
                }
            }

            mbtIO.map {
              case Some(t) => Raster(t, extent)
              case _ =>
                Raster(MultibandTile(invisiTile, invisiTile, invisiTile),
                       extent)
            }
          }
          mosaic.map(ProjectedRaster(_, WebMercator))
        }
    }

  val paintedMosaicTmsReification: TmsReification[BacksplashMosaic] =
    new TmsReification[BacksplashMosaic] {

      def tmsReification(self: BacksplashMosaic, buffer: Int)(
          implicit contextShift: ContextShift[IO]
      ): (Int, Int, Int) => IO[ProjectedRaster[MultibandTile]] =
        (z: Int, x: Int, y: Int) => {
          val imagesIO: IO[(TracingContext[IO], List[BacksplashImage[IO]])] =
            self
          (for {
            (context, imagesNel) <- imagesIO map {
              case (tracingContext, images) => (tracingContext, images.toNel)
            } flatMap {
              case (tracingContext, Some(ims)) => IO.pure((tracingContext, ims))
              case (_, None)                   => IO.raiseError(NoDataInRegionException)
            }
            bandCount = imagesNel.head.subsetBands.length
            overviewConfig <- context.childSpan("getOverviewConfig") use {
              childContext =>
                rendStore.getOverviewConfig(imagesNel.head.projectLayerId,
                                            childContext)
            }
            mosaic = chooseMosaic(z, imagesNel.head, overviewConfig, imagesNel)
            // for single band imagery, after color correction we have RGBA, so
            // the empty tile needs to be four band as well
            rendered <- context.childSpan("paintedRender") use {
              renderContext =>
                if (bandCount == 3) {
                  renderMosaicMultiband(mosaic, z, x, y, renderContext)
                } else {
                  renderMosaicSingleBand(mosaic, z, x, y, renderContext)
                }
            }
          } yield {
            ProjectedRaster(rendered, WebMercator)
          }).attempt.flatMap {
            case Left(NoDataInRegionException) =>
              IO.pure(
                ProjectedRaster(
                  Raster(
                    MultibandTile(invisiTile, invisiTile, invisiTile),
                    Extent(0, 0, 256, 256)
                  ),
                  WebMercator
                )
              )
            case Left(e)          => IO.raiseError(e)
            case Right(rasterLit) => IO.pure(rasterLit)
          }
        }
    }

  /** Private histogram retrieval method to allow for caching on/off via settings
    *
    * @param relevant
    * @return
    */
  private def getHistogramWithCache(
      relevant: BacksplashImage[IO],
      tracingContext: TracingContext[IO]
  )(implicit @cacheKeyExclude flags: Flags): IO[Array[Histogram[Double]]] =
    memoizeF(None) {
      relevant match {
        case im: BacksplashGeotiff =>
          logger.debug(
            s"Retrieving Histograms for ${im.imageId} from histogram store")
          histStore.layerHistogram(im.imageId, im.subsetBands)
        case im: Landsat8MultiTiffImage =>
          logger.debug(s"Retrieving histograms for ${im.imageId} from source")
          im.getHistogram(tracingContext)
        // Is this hilariously repetitive? Yes! But type erasure :(
        case im: Sentinel2MultiTiffImage =>
          logger.debug(s"Retrieving histograms for ${im.imageId} from source")
          im.getHistogram(tracingContext)
        case im: LandsatHistoricalMultiTiffImage =>
          logger.debug(s"Retrieving histograms for ${im.imageId} from source")
          im.getHistogram(tracingContext)
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
      def extentReification(
          self: BacksplashMosaic
      )(implicit contextShift: ContextShift[IO])
        : (Extent, CellSize) => IO[ProjectedRaster[MultibandTile]] =
        (extent: Extent, cs: CellSize) => {
          for {
            bands <- {
              self.map {
                case (_, bsiList) =>
                  bsiList.headOption match {
                    case Some(image) => image.subsetBands
                    case _           => throw NoScenesException
                  }
              }
            }
            mosaic <- if (bands.length == 3) {
              val bsm = self.map {
                case (tracingContext, bsiList) => {
                  tracingContext.childSpan("paintedMosaicExtentReification") use {
                    childContext =>
                      bsiList parTraverse { relevant =>
                        val tags =
                          Map("imageId" -> relevant.imageId.toString,
                              "projectId" -> relevant.projectId.toString)
                        for {
                          imFiber <- relevant
                            .read(extent, cs, childContext)
                            .start
                          histsFiber <- childContext.childSpan("layerHistogram",
                                                               tags) use { _ =>
                            histStore
                              .layerHistogram(
                                relevant.imageId,
                                relevant.subsetBands
                              )
                              .start
                          }
                          im <- imFiber.join
                          hists <- histsFiber.join
                          renderedTile <- childContext.childSpan("colorCorrect",
                                                                 tags) use {
                            _ =>
                              IO.pure {
                                im map { mbTile =>
                                  logger.debug(
                                    s"N bands in resulting tile: ${mbTile.bands.length}"
                                  )
                                  relevant.corrections.colorCorrect(mbTile,
                                                                    hists,
                                                                    None)
                                }
                              }
                          }
                        } yield {
                          renderedTile match {
                            case Some(mbTile) =>
                              Some(
                                Raster(mbTile.interpretAs(invisiCellType),
                                       extent))
                            case _ => None
                          }
                        }
                      }
                  }
                }.map(_.flatten.reduceOption(_ merge _))
                  .map({
                    case Some(r) => r
                    case _ =>
                      Raster(
                        MultibandTile(invisiTile, invisiTile, invisiTile),
                        extent
                      )
                  })
              }
              bsm.flatten
            } else {
              logger.debug("Creating single band extent")
              for {
                histograms <- BacksplashMosaic.getStoreHistogram(self,
                                                                 histStore)
                (tracingContext, imageList) <- self
                corrected <- tracingContext.childSpan(
                  "singleBandPaintedExtentReification") use { childContext =>
                  imageList.traverse { bsi =>
                    bsi.singleBandOptions match {
                      case Some(opts) =>
                        bsi.read(extent, cs, childContext) map {
                          case Some(mbt) =>
                            ColorRampMosaic.colorTile(mbt, histograms, opts)
                          case _ =>
                            MultibandTile(invisiTile, invisiTile, invisiTile)
                        }
                      case _ =>
                        IO.raiseError(
                          SingleBandOptionsException(
                            "Must specify single band options when requesting single band visualization.")
                        )
                    }
                  }
                }
              } yield {
                corrected.reduceOption(_ merge _) match {
                  case Some(r) => Raster(r, extent)
                  case _ =>
                    Raster(
                      MultibandTile(invisiTile, invisiTile, invisiTile),
                      extent
                    )
                }
              }
            }
          } yield ProjectedRaster(mosaic, WebMercator)
        }
    }

  implicit val rawMosaicExtentReification: ExtentReification[BacksplashMosaic] =
    new ExtentReification[BacksplashMosaic] {

      def extentReification(
          self: BacksplashMosaic
      )(implicit contextShift: ContextShift[IO])
        : (Extent, CellSize) => IO[ProjectedRaster[MultibandTile]] =
        (extent: Extent, cs: CellSize) => {
          val mosaic = self.map {
            case (tracingContext, listBsi) =>
              tracingContext.childSpan("rawMosaicExtentReification") use {
                childContext =>
                  for {
                    mbts <- {
                      childContext.childSpan("rawMosaicExtentReification.reads") use {
                        grandContext =>
                          listBsi.traverse({ relevant =>
                            relevant.read(extent, cs, grandContext)
                          })
                      }
                    }
                  } yield {
                    val tiles = mbts.collect({ case Some(mbTile) => mbTile })
                    val rasters = tiles.map(Raster(_, extent))
                    rasters.reduceOption(_ merge _) match {
                      case Some(r) => r
                      case _ =>
                        Raster(
                          MultibandTile(invisiTile, invisiTile, invisiTile),
                          extent
                        )
                    }
                  }
              }
          }
          mosaic.flatten map {
            ProjectedRaster(_, WebMercator)
          }
        }
    }

  implicit val extentSemigroup: Semigroup[Extent] = new Semigroup[Extent] {
    def combine(x: Extent, y: Extent): Extent = x.combine(y)
  }

  implicit val mosaicHasRasterExtents: HasRasterExtents[BacksplashMosaic] =
    new HasRasterExtents[BacksplashMosaic] {
      def rasterExtents(
          self: BacksplashMosaic
      )(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] = {
        val mosaic = self.flatMap {
          case (tracingContext, bsiList) =>
            tracingContext.childSpan("mosaicRasterExtents") use {
              childContext =>
                bsiList.traverse({ img =>
                  img.getRasterSource(childContext) map { rs =>
                    val rasterExtents = rs.resolutions map { res =>
                      ReprojectRasterExtent(RasterExtent(res.extent,
                                                         res.cellwidth,
                                                         res.cellheight,
                                                         res.cols.toInt,
                                                         res.rows.toInt),
                                            rs.crs,
                                            WebMercator)
                    }
                    rasterExtents
                  }
                })
            }
        }
        mosaic.map(_.flatten.toNel.getOrElse(
          throw new MetadataException("Cannot get raster extent from mosaic.")))
      }
    }
}
