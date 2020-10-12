package com.rasterfoundry.common.utils

import com.rasterfoundry.common.{BacksplashGeoTiffInfo, BacksplashGeotiffReader}

import cats.effect.{Blocker, ContextShift, Sync}
import cats.syntax.functor._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.geotiff.GeoTiffRasterSource
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.raster.resample.ResampleMethod
import geotrellis.vector._

import scala.concurrent.ExecutionContext

class CogUtils[F[_]: Sync](
    contextShift: ContextShift[F],
    executionContext: ExecutionContext
) extends LazyLogging {

  val blocker = Blocker.liftExecutionContext(executionContext)

  def getTiffExtent(
      uri: String
  ): F[Projected[MultiPolygon]] =
    blocker.delay({
      val rasterSource = GeoTiffRasterSource(uri)
      Projected(
        MultiPolygon(
          rasterSource.extent
            .reproject(rasterSource.crs, WebMercator)
            .toPolygon()
        ),
        3857
      )
    })(Sync[F], contextShift) map { data =>
      logger.debug(s"Got tiff extent for $uri")
      data
    }

  def histogramFromUri(
      uri: String,
      buckets: Int = 80
  ): F[Option[Array[Histogram[Double]]]] =
    blocker.delay({
      val rasterSource = GeoTiffRasterSource(uri)
      val largestCellSize = rasterSource.resolutions
        .maxBy(_.resolution)

      val resampleTarget = TargetCellSize(largestCellSize)

      rasterSource
        .resample(
          resampleTarget,
          ResampleMethod.DEFAULT,
          OverviewStrategy.DEFAULT
        )
        .read(rasterSource.extent)
        .map(_.tile.bands.map(_.histogramDouble(buckets)).toArray)

    })(Sync[F], contextShift) map { data =>
      logger.debug(s"Got histogram for $uri")
      data
    }

  def getGeoTiffInfo(
      uri: String
  ): F[BacksplashGeoTiffInfo] =
    blocker.delay(
      BacksplashGeotiffReader.getGeotiffInfo(uri)
    )(Sync[F], contextShift) map { data =>
      logger.debug(s"Got backsplash geotiff info for $uri")
      data
    }
}
