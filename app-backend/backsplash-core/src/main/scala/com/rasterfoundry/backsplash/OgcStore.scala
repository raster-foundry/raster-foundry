package com.rasterfoundry.backsplash

import cats.effect.IO
import geotrellis.server.ogc.RasterSourcesModel
import simulacrum._

import java.util.UUID

@typeclass trait OgcStore[A] {
  @op("getModel") def getModel(self: A, id: UUID): IO[RasterSourcesModel]
}
