package com.rasterfoundry.backsplash

import cats.effect.IO
import geotrellis.raster.histogram.Histogram
import geotrellis.server.ogc.wms.WmsModel
import geotrellis.server.ogc.wcs.WcsModel
import opengis.wms.Service
import simulacrum._
import java.util.UUID

import com.colisweb.tracing.TracingContext

@typeclass trait OgcStore[A] {
  @op("getWcsModel") def getWcsModel(
      self: A,
      id: UUID,
      tracingContext: TracingContext[IO]): IO[WcsModel]
  @op("getWmsModel") def getWmsModel(
      self: A,
      id: UUID,
      tracingContext: TracingContext[IO]): IO[WmsModel]
  @op("getWmsServiceMetadata") def getWmsServiceMetadata(
      self: A,
      id: UUID,
      tracingContext: TracingContext[IO]): IO[Service]
  @op("getLayerHistogram") def getLayerHistogram(
      self: A,
      id: UUID,
      tracingContext: TracingContext[IO]): IO[List[Histogram[Double]]]
}
