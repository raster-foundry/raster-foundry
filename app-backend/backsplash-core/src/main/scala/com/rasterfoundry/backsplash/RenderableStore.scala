package com.rasterfoundry.backsplash

import cats.data.{NonEmptyList => NEL}
import cats.effect.IO
import com.rasterfoundry.datamodel.BandOverride
import geotrellis.vector.{Polygon, Projected}
import simulacrum._
import java.util.UUID

import com.colisweb.tracing.{NoOpTracingContext, TracingContext}

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
      tracingContext: TracingContext[IO] = NoOpTracingContext[IO]()
  ): BacksplashMosaic

}
