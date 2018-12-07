package com.rasterfoundry.backsplash

import cats.data.{NonEmptyList => NEL}
import cats.effect._
import com.azavea.maml.ast._
import geotrellis.vector.{Polygon, Projected}
import geotrellis.server._
import simulacrum._

import java.util.UUID

case class BandOverride(
    red: Int,
    green: Int,
    blue: Int
)

@typeclass trait ProjectStore[A] {
  @op("read") def read(self: A,
                       projId: UUID,
                       window: Option[Projected[Polygon]],
                       bandOverride: Option[BandOverride],
                       imageSubset: Option[NEL[UUID]]): BacksplashMosaic
}
