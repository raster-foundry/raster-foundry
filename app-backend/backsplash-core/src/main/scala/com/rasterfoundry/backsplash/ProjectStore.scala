package com.rasterfoundry.backsplash

import cats.data.{NonEmptyList => NEL}
import cats.effect.IO
import com.rasterfoundry.datamodel.BandOverride
import geotrellis.vector.{Polygon, Projected}
import simulacrum._

import java.util.UUID

@typeclass trait ProjectStore[A] {
  @op("read") def read(
      self: A,
      projId: UUID,
      window: Option[Projected[Polygon]],
      bandOverride: Option[BandOverride],
      imageSubset: Option[NEL[UUID]]
  ): BacksplashMosaic

  @op("getOverviewLocation") def getOverviewLocation(
      self: A,
      projId: UUID
  ): IO[Option[String]]
}
