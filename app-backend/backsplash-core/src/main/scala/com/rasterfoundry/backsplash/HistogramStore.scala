package com.rasterfoundry.backsplash

import cats.effect.IO
import geotrellis.raster.histogram._
import simulacrum._

import java.util.UUID

@typeclass trait HistogramStore[A] {
  @op("layerHistogram") def layerHistogram(
      self: A,
      layerId: UUID,
      subsetBands: List[Int]
  ): IO[Array[Histogram[Double]]]

  @op("projectLayerHistogram") def projectLayerHistogram(
      self: A,
      projectLayerId: UUID,
      subsetBands: List[Int]
  ): IO[Array[Histogram[Double]]]
}
