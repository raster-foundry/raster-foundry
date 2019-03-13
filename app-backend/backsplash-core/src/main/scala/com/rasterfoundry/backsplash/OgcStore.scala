package com.rasterfoundry.backsplash

import cats.effect.IO
import geotrellis.server.ogc.conf.OgcSourceConf
import simulacrum._

import java.util.UUID

@typeclass trait OgcStore[A] {
  @op("getConfig") def getConfig(self: A, id: UUID): IO[OgcSourceConf]
}
