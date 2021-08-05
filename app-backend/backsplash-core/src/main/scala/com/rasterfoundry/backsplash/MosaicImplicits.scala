package com.rasterfoundry.backsplash

import com.rasterfoundry.backsplash.HistogramStore.ToHistogramStoreOps
import com.rasterfoundry.backsplash.RenderableStore._
import com.rasterfoundry.backsplash.ToolStore._
import com.rasterfoundry.backsplash.color._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.datamodel.SingleBandOptions

import cats.Semigroup
import cats.data.{NonEmptyList => NEL}
import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import com.colisweb.tracing.core.TracingContext
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4.WebMercator
import geotrellis.raster.{io => _, _}
import geotrellis.raster.reproject._
import geotrellis.server._
import geotrellis.vector.{io => _, _}
import io.chrisdavenport.log4cats.Logger

class MosaicImplicits[HistStore: HistogramStore](histStore: HistStore)
    extends ToHistogramStoreOps
    with ToRenderableStoreOps
    with ToToolStoreOps
    with LazyLogging {

  val streamConcurrency = Config.parallelism.streamConcurrency

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
    val ioMBTwithSBO: IO[List[MBTTriple]] = tracingContext.span(
      "renderMosaicSingleBand"
    ) use { context =>
      mosaic
        .parTraverseN(streamConcurrency)((relevant: BacksplashImage[IO]) => {
          logger.debug(s"Band Subset Required: ${relevant.subsetBands}")
          relevant.read(z, x, y, context) map {
            (_, relevant.singleBandOptions, relevant.metadata.noDataValue)
          }
        })
        .map(nel =>
          nel.collect({
            case (Some(mbtile), Some(sbo), nd) => (mbtile, sbo, nd)
          })
        )
    }
    for {
      imagesNel <- ioMBTwithSBO map { _.toNel } flatMap {
        case Some(ims) => IO.pure(ims)
        case None      => IO.raiseError(NoScenesException)
      }
      firstIm = mosaic.head
      histograms <-
        tracingContext.span("renderMosaicSingleBand.histogram") use { context =>
          firstIm.singleBandOptions map { _.band } map { bd =>
            histStore.projectLayerHistogram(
              firstIm.projectLayerId,
              List(bd),
              context
            )
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
          Raster(
            ColorRampMosaic.colorTile(
              interpretAsFallback(tile, nd),
              List(combinedHistogram),
              sbo
            ),
            extent
          )
        case someTiles =>
          val outTile = someTiles.foldLeft(
            MultibandTile(invisiTile)
          )((baseTile: MultibandTile, triple2: MBTTriple) =>
            interpretAsFallback(baseTile, firstNd) merge interpretAsFallback(
              triple2._1,
              firstNd
            )
          )
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

  def interpretAsFallback[T1, T2](
      tile: MultibandTile,
      noData: Option[Double]
  ): MultibandTile =
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
    val ioMBT = tracingContext.span("renderMosaic") use { context =>
      mosaic
        .parTraverseN(streamConcurrency)((relevant: BacksplashImage[IO]) => {
          val tags = Map(
            "sceneId" -> relevant.imageId.toString,
            "projectId" -> relevant.projectId.toString,
            "projectLayerId" -> relevant.projectLayerId.toString,
            "zoom" -> z.toString
          )

          context
            .span("renderMosaicMultiband.renderBacksplashImage", tags) use {
            childContext =>
              for {
                imFiber <- relevant.read(z, x, y, childContext).start
                histsFiber <- {
                  childContext.span(
                    "renderMosaicMultiband.readHistogram",
                    tags
                  ) use { _ =>
                    getHistogramWithCache(relevant, childContext)
                  }
                }.start
                (im, hists) <- (imFiber, histsFiber).tupled.join
                resultTile <-
                  childContext
                    .span("renderMosaicMultiband.colorCorrect") use { _ =>
                    IO {
                      im map { mbTile =>
                        val noDataValue = getNoDataValue(mbTile.cellType)
                        logger.debug(
                          s"NODATA Value: $noDataValue with CellType: ${mbTile.cellType}"
                        )
                        if (relevant.disableColorCorrect) {
                          mbTile
                        } else {
                          relevant.corrections.colorCorrect(
                            mbTile,
                            hists,
                            relevant.metadata.noDataValue orElse noDataValue orElse Some(
                              0
                            ),
                            relevant.lowerQuantile,
                            relevant.upperQuantile
                          )
                        }
                      }
                    }
                  }
              } yield {
                resultTile
              }
          }
        })
        .map(nel => nel.collect({ case Some(tile) => tile }))
    }
    mergeTiles(ioMBT).map {
      case Some(t) => Raster(t, extent)
      case _ =>
        Raster(
          MultibandTile(
            invisiTile,
            invisiTile,
            invisiTile
          ),
          extent
        )
    }
  }

  def rawMosaicTmsReification(implicit
      cs: ContextShift[IO]
  ): TmsReification[IO, BacksplashMosaic] =
    new TmsReification[IO, BacksplashMosaic] {

      def tmsReification(
          self: BacksplashMosaic,
          buffer: Int
      ): (Int, Int, Int) => IO[ProjectedRaster[MultibandTile]] =
        (z: Int, x: Int, y: Int) => {
          val extent =
            BacksplashImage.tmsLevels(z).mapTransform.keyToExtent(x, y)
          val mosaic = {
            val mbtIO = self.flatMap {
              case (tracingContext, listBsi) =>
                tracingContext.span("getMergedRawMosaic") use { childContext =>
                  val listIO = listBsi.parTraverseN(streamConcurrency) { bsi =>
                    bsi.read(z, x, y, childContext)
                  }
                  childContext.span("mergeRawTiles") use { _ =>
                    listIO.map(_.flatten.reduceOption(_ merge _))
                  }
                }
            }

            mbtIO.map {
              case Some(t) => Raster(t, extent)
              case _ =>
                Raster(
                  MultibandTile(
                    invisiTile,
                    invisiTile,
                    invisiTile
                  ),
                  extent
                )
            }
          }
          mosaic.map(ProjectedRaster(_, WebMercator))
        }
    }

  def paintedMosaicTmsReification(implicit
      cs: ContextShift[IO]
  ): TmsReification[IO, BacksplashMosaic] =
    new TmsReification[IO, BacksplashMosaic] {

      def tmsReification(
          self: BacksplashMosaic,
          buffer: Int
      ): (Int, Int, Int) => IO[ProjectedRaster[MultibandTile]] =
        (z: Int, x: Int, y: Int) => {
          val imagesIO: IO[(TracingContext[IO], List[BacksplashImage[IO]])] =
            self
          (for {
            (context, mosaic) <- imagesIO map {
              case (tracingContext, images) => (tracingContext, images.toNel)
            } flatMap {
              case (tracingContext, Some(ims)) => IO.pure((tracingContext, ims))
              case (_, None)                   => IO.raiseError(NoDataInRegionException)
            }
            bandCount = mosaic.head.subsetBands.length
            // for single band imagery, after color correction we have RGBA, so
            // the empty tile needs to be four band as well
            rendered <- context.span("paintedRender") use { renderContext =>
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
                    MultibandTile(
                      invisiTile,
                      invisiTile,
                      invisiTile
                    ),
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
  ): IO[Array[Histogram[Double]]] = {
    logger.debug(
      s"Retrieving Histograms for ${relevant.imageId} from histogram store"
    )
    histStore.layerHistogram(
      relevant.imageId,
      relevant.subsetBands,
      tracingContext
    )
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
  def paintedMosaicExtentReification(implicit
      contextShift: ContextShift[IO],
      logger: Logger[IO]
  ): ExtentReification[IO, BacksplashMosaic] =
    new ExtentReification[IO, BacksplashMosaic] {

      // For now at least this is only called in the tile server, where we always have a cell size
      // for the call.
      @SuppressWarnings(Array("OptionGet"))
      def extentReification(
          self: BacksplashMosaic
      ): (Extent, Option[CellSize]) => IO[ProjectedRaster[MultibandTile]] =
        (extent: Extent, cs: Option[CellSize]) => {
          for {
            bands <- self.map {
              case (_, bsiList) =>
                bsiList.headOption match {
                  case Some(image) => image.subsetBands
                  case _           => throw NoScenesException
                }
            }
            mosaic <-
              if (bands.length == 3) {
                val bsm = self.map {
                  case (tracingContext, bsiList) => {
                      tracingContext.span(
                        "paintedMosaicExtentReification"
                      ) use { childContext =>
                        bsiList.parTraverseN(streamConcurrency) { relevant =>
                          val tags =
                            Map(
                              "imageId" -> relevant.imageId.toString,
                              "projectId" -> relevant.projectId.toString
                            )
                          for {
                            imFiber <-
                              relevant
                                .read(extent, cs.get, childContext)
                                .start
                            histsFiber <- childContext.span(
                              "layerHistogram",
                              tags
                            ) use { context =>
                              histStore
                                .layerHistogram(
                                  relevant.imageId,
                                  relevant.subsetBands,
                                  context
                                )
                                .start
                            }
                            im <- imFiber.join
                            hists <- histsFiber.join
                            renderedTile <- childContext.span(
                              "colorCorrect",
                              tags
                            ) use { _ =>
                              IO.pure {
                                im map { mbTile =>
                                  logger.debug(
                                    s"N bands in resulting tile: ${mbTile.bands.length}"
                                  )
                                  if (relevant.disableColorCorrect) {
                                    mbTile
                                  } else {
                                    relevant.corrections.colorCorrect(
                                      mbTile,
                                      hists,
                                      None,
                                      relevant.lowerQuantile,
                                      relevant.upperQuantile
                                    )
                                  }
                                }
                              }
                            }
                          } yield {
                            renderedTile match {
                              case Some(mbTile) =>
                                Some(
                                  Raster(
                                    mbTile.interpretAs(
                                      invisiCellType
                                    ),
                                    extent
                                  )
                                )
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
                            MultibandTile(
                              invisiTile,
                              invisiTile,
                              invisiTile
                            ),
                            extent
                          )
                      })
                }
                bsm.flatten
              } else {
                logger.debug("Creating single band extent")
                for {
                  histograms <- BacksplashMosaic.getStoreHistogram(
                    self,
                    histStore
                  )
                  (tracingContext, imageList) <- self
                  corrected <- tracingContext.span(
                    "singleBandPaintedExtentReification"
                  ) use { childContext =>
                    imageList.parTraverseN(streamConcurrency) { bsi =>
                      bsi.singleBandOptions match {
                        case Some(opts) =>
                          bsi.read(extent, cs.get, childContext) map {
                            case Some(mbt) =>
                              ColorRampMosaic.colorTile(mbt, histograms, opts)
                            case _ =>
                              MultibandTile(
                                invisiTile,
                                invisiTile,
                                invisiTile
                              )
                          }
                        case _ =>
                          IO.raiseError(
                            SingleBandOptionsException(
                              "Must specify single band options when requesting single band visualization."
                            )
                          )
                      }
                    }
                  }
                } yield {
                  corrected.reduceOption(_ merge _) match {
                    case Some(r) => Raster(r, extent)
                    case _ =>
                      Raster(
                        MultibandTile(
                          invisiTile,
                          invisiTile,
                          invisiTile
                        ),
                        extent
                      )
                  }
                }
              }
          } yield ProjectedRaster(mosaic, WebMercator)
        }
    }

  implicit def rawMosaicExtentReification(implicit
      contextShift: ContextShift[IO]
  ): ExtentReification[IO, BacksplashMosaic] =
    new ExtentReification[IO, BacksplashMosaic] {
      // For now at least this is only called in the tile server, where we always have a cell size
      // for the call.
      @SuppressWarnings(Array("OptionGet"))
      def extentReification(
          self: BacksplashMosaic
      ): (Extent, Option[CellSize]) => IO[ProjectedRaster[MultibandTile]] =
        (extent: Extent, cs: Option[CellSize]) => {
          val mosaic = self.map {
            case (tracingContext, listBsi) =>
              tracingContext.span("rawMosaicExtentReification") use {
                childContext =>
                  for {
                    mbts <- {
                      childContext.span(
                        "rawMosaicExtentReification.reads"
                      ) use { grandContext =>
                        listBsi.parTraverseN(streamConcurrency) { relevant =>
                          relevant.read(extent, cs.get, grandContext)
                        }
                      }
                    }
                  } yield {
                    val tiles = mbts.collect({ case Some(mbTile) => mbTile })
                    val rasters = tiles.map(Raster(_, extent))
                    rasters.reduceOption(_ merge _) match {
                      case Some(r) => r
                      case _ =>
                        Raster(
                          MultibandTile(
                            invisiTile,
                            invisiTile,
                            invisiTile
                          ),
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

  implicit def mosaicHasRasterExtents(implicit
      contextShift: ContextShift[IO]
  ): HasRasterExtents[IO, BacksplashMosaic] =
    new HasRasterExtents[IO, BacksplashMosaic] {
      def rasterExtents(
          self: BacksplashMosaic
      ): IO[NEL[RasterExtent]] = {
        val mosaic = self.flatMap {
          case (tracingContext, bsiList) =>
            tracingContext.span("mosaicRasterExtents") use { childContext =>
              bsiList.parTraverseN(streamConcurrency)({ img =>
                img.getRasterSource(childContext) map { rs =>
                  val rasterExtents = rs.resolutions map { cellSize =>
                    ReprojectRasterExtent(
                      RasterExtent(
                        rs.extent,
                        cellSize.width,
                        cellSize.height,
                        rs.cols.toInt,
                        rs.rows.toInt
                      ),
                      rs.crs,
                      WebMercator
                    )
                  }
                  rasterExtents
                }
              })
            }
        }
        mosaic.map(
          _.flatten.toNel.getOrElse(
            throw new MetadataException("Cannot get raster extent from mosaic.")
          )
        )
      }
    }
}
