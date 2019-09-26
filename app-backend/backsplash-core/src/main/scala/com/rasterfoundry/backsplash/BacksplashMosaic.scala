package com.rasterfoundry.backsplash

import geotrellis.contrib.vlm.MosaicRasterSource
import geotrellis.proj4.CRS
import geotrellis.raster.histogram._
import geotrellis.server._
import cats.implicits._
import cats.data.{NonEmptyList => _}
import cats.data.Validated._
import cats.effect._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.HistogramStore.ToHistogramStoreOps

object BacksplashMosaic extends ToHistogramStoreOps {

  def toRasterSource(bsm: BacksplashMosaic)(
      implicit contextShift: ContextShift[IO]): IO[MosaicRasterSource] = {
    bsm flatMap {
      case (tracingContext, backsplashImages) =>
        tracingContext.childSpan("bsm.toRasterSource") use { childContext =>
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

  def getRasterSourceOriginalCRS(bsm: BacksplashMosaic)(
      implicit contextShift: ContextShift[IO]): IO[List[CRS]] = {
    bsm flatMap {
      case (tracingContext, backsplashImages) =>
        tracingContext.childSpan("bsm.getRasterSourceOriginalCRS") use {
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
      implicit hasRasterExtents: HasRasterExtents[BacksplashMosaic],
      extentReification: ExtentReification[BacksplashMosaic],
      cs: ContextShift[IO]) = {
    LayerHistogram.identity(mosaic, 4000)
  }

  /** We're in the non-Nil branch of the match, so we definitely have histograms
    * at the point where we're asking for the head of the list.
    * Also, messing with the map instead of match thing messes up the types for reasons
    * that I disagree with, so suppressing.
    */
  @SuppressWarnings(Array("TraversableHead", "PartialFunctionInsteadOfMatch"))
  def getStoreHistogram[T: HistogramStore](mosaic: BacksplashMosaic,
                                           histStore: T)(
      implicit hasRasterExtents: HasRasterExtents[BacksplashMosaic],
      extentReification: ExtentReification[BacksplashMosaic],
      cs: ContextShift[IO]): IO[List[Histogram[Double]]] =
    for {
      (tracingContext, allImages) <- mosaic
      histArrays <- tracingContext.childSpan("getAllImageHistograms") use {
        childContext =>
          allImages parTraverse { im =>
            {
              childContext.childSpan("layerHistogram") use { _ =>
                histStore.layerHistogram(im.imageId, im.subsetBands)
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
                StreamingHistogram(255): Histogram[Double]))(
              (histArr1: Array[Histogram[Double]],
               histArr2: Array[Histogram[Double]]) => {
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
