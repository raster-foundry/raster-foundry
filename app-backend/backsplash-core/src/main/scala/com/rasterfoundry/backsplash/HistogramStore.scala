package com.rasterfoundry.backsplash

import cats.effect.IO
import geotrellis.raster.histogram._
import simulacrum._
import java.util.UUID

import com.colisweb.tracing.TracingContext

@typeclass trait HistogramStore[A] {
  @op("layerHistogram") def layerHistogram(
      self: A,
      layerId: UUID,
      subsetBands: List[Int],
      tracingContext: TracingContext[IO]
  ): IO[Array[Histogram[Double]]]

  @op("projectLayerHistogram") def projectLayerHistogram(
      self: A,
      projectLayerId: UUID,
      subsetBands: List[Int],
      tracingContext: TracingContext[IO]
  ): IO[Array[Histogram[Double]]]
}
