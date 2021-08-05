package com.rasterfoundry.backsplash

import com.rasterfoundry.backsplash.HistogramStore.ToHistogramStoreOps
import com.rasterfoundry.backsplash.error._

import cats.data.Validated._
import cats.data.{NonEmptyList => _}
import cats.effect._
import cats.implicits._
import geotrellis.proj4.CRS
import geotrellis.raster.MosaicRasterSource
import geotrellis.raster.histogram._
import geotrellis.server._
import io.chrisdavenport.log4cats.Logger

object BacksplashMosaic extends ToHistogramStoreOps {

  def toRasterSource(
      bsm: BacksplashMosaic
  )(implicit contextShift: ContextShift[IO]): IO[MosaicRasterSource] = {
    bsm flatMap {
      case (tracingContext, backsplashImages) =>
        tracingContext.span("bsm.toRasterSource") use { childContext =>
          backsplashImages.toNel match {
            case Some(images) =>
              images parTraverse { image =>
                image.getRasterSource(childContext)
              } map { rasterSourceList =>
                MosaicRasterSource(rasterSourceList, rasterSourceList.head.crs)
              }
            case _ =>
              IO.raiseError(NoScenesException)
          }
        }
    }
  }

  def getRasterSourceOriginalCRS(
      bsm: BacksplashMosaic
  )(implicit contextShift: ContextShift[IO]): IO[List[CRS]] = {
    bsm flatMap {
      case (tracingContext, backsplashImages) =>
        tracingContext.span("bsm.getRasterSourceOriginalCRS") use {
          childContext =>
            backsplashImages.toNel match {
              case Some(images) =>
                images parTraverse { image =>
                  image.getRasterSource(childContext)
                } map { rasterSourceList =>
                  rasterSourceList.map(_.crs).toList.distinct
                }
              case _ =>
                IO.raiseError(NoScenesException)
            }
        }
    }
  }

  def first(bsm: BacksplashMosaic): IO[Option[BacksplashImage[IO]]] = {
    bsm.map { case (_, backsplashImages) => backsplashImages.headOption }
  }

  def layerHistogram(mosaic: BacksplashMosaic)(
      implicit
      hasRasterExtents: HasRasterExtents[IO, BacksplashMosaic],
      extentReification: ExtentReification[IO, BacksplashMosaic],
      cs: ContextShift[IO],
      logger: Logger[IO]) = {
    LayerHistogram.concurrent(mosaic, 4000)
  }

  /** We're in the non-Nil branch of the match, so we definitely have histograms
    * at the point where we're asking for the head of the list.
    * Also, messing with the map instead of match thing messes up the types for reasons
    * that I disagree with, so suppressing.
    */
  @SuppressWarnings(Array("TraversableHead", "PartialFunctionInsteadOfMatch"))
  def getStoreHistogram[T: HistogramStore](
      mosaic: BacksplashMosaic,
      histStore: T
  )(implicit
    hasRasterExtents: HasRasterExtents[IO, BacksplashMosaic],
    extentReification: ExtentReification[IO, BacksplashMosaic],
    cs: ContextShift[IO],
    logger: Logger[IO]): IO[List[Histogram[Double]]] =
    for {
      (tracingContext, allImages) <- mosaic
      histArrays <- tracingContext.span("getAllImageHistograms") use {
        childContext =>
          allImages parTraverse { im =>
            {
              childContext.span("layerHistogram") use { histogramContext =>
                histStore.layerHistogram(
                  im.imageId,
                  im.subsetBands,
                  histogramContext
                )
              }
            }
          }
      }
      result <- histArrays match {
        case Nil =>
          layerHistogram(mosaic) map {
            case Valid(hists) => hists
            case Invalid(e) =>
              throw new MetadataException(s"Could not produce histograms: $e")
          }
        case arrs =>
          val hists = arrs
            .foldLeft(
              Array.fill(arrs.head.length)(
                StreamingHistogram(255): Histogram[Double]
              )
            )(
              (
                  histArr1: Array[Histogram[Double]],
                  histArr2: Array[Histogram[Double]]
              ) => {
                histArr1 zip histArr2 map {
                  case (h1, h2) => h1 merge h2
                }
              }
            )
            .toList
          IO.pure {
            hists
          }
      }
    } yield {
      result
    }

}
