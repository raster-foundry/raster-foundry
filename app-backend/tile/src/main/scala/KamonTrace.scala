package com.azavea.rf.tile

import kamon.Kamon
import kamon.metric.instrument.Time
import kamon.trace.Tracer

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait KamonTrace {
  def traceName[T](name: String)(code: => T): T = {
    Kamon.metrics.counter(name).increment()
    Tracer.currentContext.withNewSegment(name, "com.azavea.rf", "tile")(code)
  }

  def timedFuture[T](name: String)(future: Future[T]) = {
    val start = System.currentTimeMillis()
    future.onComplete({
      case _ => {
        val h = Kamon.metrics.histogram(name, Time.Milliseconds)
        h.record(System.currentTimeMillis() - start)
      }
    })
    future
  }

  def timed[T](name: String)(f: => T): T = {
    val start = System.currentTimeMillis()
    val result = f    // call-by-name
    val h = Kamon.metrics.histogram(name, Time.Milliseconds)
    h.record(System.currentTimeMillis() - start)
    result
  }
}
