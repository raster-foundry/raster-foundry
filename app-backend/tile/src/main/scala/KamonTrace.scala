package com.azavea.rf.tile

import kamon.Kamon
import kamon.trace.Tracer

trait KamonTrace {
  def traceName[T](name: String)(code: => T): T = {
    Kamon.metrics.counter(name).increment()
    Tracer.currentContext.withNewSegment(name, "com.azavea.rf", "tile")(code)
  }
}
