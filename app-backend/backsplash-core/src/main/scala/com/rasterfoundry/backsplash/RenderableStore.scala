package com.rasterfoundry.backsplash

import com.rasterfoundry.datamodel.BandOverride

import cats.data.{NonEmptyList => NEL}
import cats.effect.IO
import com.colisweb.tracing.context.NoOpTracingContext
import com.colisweb.tracing.core.TracingContext
import geotrellis.vector.{Polygon, Projected}
import simulacrum._

import java.util.UUID

// RenderableStore
// currently scenes and layers
//
@typeclass trait RenderableStore[A] {

  @op("read") def read(
      self: A,
      renderableId: UUID,
      window: Option[Projected[Polygon]],
      bandOverride: Option[BandOverride],
      imageSubset: Option[NEL[UUID]],
      tracingContext: TracingContext[IO] = NoOpTracingContext[IO]("no-op-read")
  ): BacksplashMosaic

}
