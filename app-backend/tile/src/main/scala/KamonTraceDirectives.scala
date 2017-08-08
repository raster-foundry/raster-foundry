package com.azavea.rf.tile

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.directives.BasicDirectives
import kamon.Kamon
import kamon.trace.Tracer

trait KamonTraceDirectives extends BasicDirectives {
  def traceName(name: String, tags: Map[String, String] = Map.empty): Directive0 = mapRequest { req ⇒
    tags.foreach { case (key, value) ⇒ Tracer.currentContext.addTag(key, value) }
    Kamon.metrics.counter(name).increment()
    Tracer.currentContext.rename(name)
    req
  }
}

object KamonTraceDirectives extends KamonTraceDirectives
