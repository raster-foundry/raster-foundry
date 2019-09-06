package com.rasterfoundry.http4s

import cats.effect.{ContextShift, IO, Timer}
import com.colisweb.tracing._
import com.colisweb.tracing.TracingContext.{
  TracingContextBuilder,
  TracingContextResource
}

import java.util.UUID

object LoggingTracer {

  def tracingContextBuilder(implicit contextShift: ContextShift[IO],
                            timer: Timer[IO]): TracingContextBuilder[IO] = {
    new TracingContextBuilder[IO] {

      def apply(
          operationName: String,
          tags: Map[String, String] = Map.empty): TracingContextResource[IO] = {
        val idGenerator: Option[IO[String]] = Some(
          IO(tags.getOrElse("TraceId", s"${UUID.randomUUID()}") match {
            case "" => s"${UUID.randomUUID()}"
            case s  => s
          }))

        LoggingTracingContext[IO](idGenerator = idGenerator)(operationName,
                                                             tags)
      }
    }
  }
}
