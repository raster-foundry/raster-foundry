package com.rasterfoundry.common.utils

import com.rasterfoundry.common.{BacksplashGeoTiffInfo, BacksplashGeotiffReader}

import cats.effect.{Blocker, ContextShift, Sync}
import cats.syntax.functor._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.gdal.GDALRasterSource
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
      rasterSource: GDALRasterSource
  ): F[Projected[MultiPolygon]] =
    Sync[F].delay {
      Projected(
        MultiPolygon(
          rasterSource.extent
            .reproject(rasterSource.crs, WebMercator)
            .toPolygon()
        ),
        3857
      )
    }

  def histogramFromUri(
      rasterSource: GDALRasterSource,
      buckets: Int = 80
  ): F[Option[Array[Histogram[Double]]]] =
    Sync[F].delay {
      val largestCellSize = rasterSource.resolutions
        .maxBy(_.resolution)

      val resampleTarget = TargetCellSize(largestCellSize)
      rasterSource
        .resample(
          resampleTarget,
          ResampleMethod.DEFAULT,
          OverviewStrategy.DEFAULT
        )
        .read
        .map(_.tile.bands.map(_.histogramDouble(buckets)).toArray)

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
