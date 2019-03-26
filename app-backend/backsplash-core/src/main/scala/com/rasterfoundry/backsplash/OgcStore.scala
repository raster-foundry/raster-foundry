package com.rasterfoundry.backsplash

import cats.effect.IO
import geotrellis.raster.histogram.Histogram
import geotrellis.server.ogc.wms.WmsModel
import geotrellis.server.ogc.wcs.WcsModel
import opengis.wms.Service
import simulacrum._

import java.util.UUID

@typeclass trait OgcStore[A] {
  @op("getWcsModel") def getWcsModel(self: A, id: UUID): IO[WcsModel]
  @op("getWmsModel") def getWmsModel(self: A, id: UUID): IO[WmsModel]
  @op("getWmsServiceMetadata") def getWmsServiceMetadata(self: A,
                                                         id: UUID): IO[Service]
  @op("getLayerHistogram") def getLayerHistogram(
      self: A,
      id: UUID): IO[List[Histogram[Double]]]
}
