package com.rasterfoundry.backsplash

import cats.effect.IO
import simulacrum._

import java.util.UUID

@typeclass trait ToolStore[A] {
  @op("read") def read[B](self: A, analysisId: UUID): IO[PaintableTool[B]]
}
