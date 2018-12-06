package com.rasterfoundry.backsplash

import cats.effect._
import com.azavea.maml.ast._
import geotrellis.server._
import simulacrum._

import java.util.UUID

@typeclass trait ProjectStore[A] {
  @op("read") def read(self: A, projId: UUID): BacksplashMosaic
}
