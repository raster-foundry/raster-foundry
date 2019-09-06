package com.rasterfoundry.http4s

import cats.effect.{ContextShift, IO}
import com.colisweb.tracing._
import com.colisweb.tracing.TracingContext.{
  TracingContextBuilder,
  TracingContextResource
}
import io.opentracing.Span
import io.jaegertracing.Configuration
import io.jaegertracing.Configuration._
import io.jaegertracing.internal.{JaegerTracer => JT}

object JaegerTracer {

  def initTracer(service: String): JT = {
    val samplerConfig: SamplerConfiguration =
      SamplerConfiguration.fromEnv().withType("const").withParam(1)
    val senderConfig: SenderConfiguration =
      SenderConfiguration.fromEnv().withAgentHost("jaeger.service.internal")
    val reporterConfig: ReporterConfiguration =
      ReporterConfiguration
        .fromEnv()
        .withLogSpans(true)
        .withSender(senderConfig)
    val config: Configuration =
      new Configuration(service)
        .withSampler(samplerConfig)
        .withReporter(reporterConfig)
    config.getTracer()
  }

  def tracingContextBuilder(
      implicit contextShift: ContextShift[IO]): TracingContextBuilder[IO] = {
    new TracingContextBuilder[IO] {

      def apply(
          operationName: String,
          tags: Map[String, String] = Map.empty): TracingContextResource[IO] = {

        val service = tags.getOrElse("service", "raster-foundry")
        OpenTracingContext[IO, JT, Span](initTracer(service))(operationName,
                                                              tags)
      }
    }
  }
}
