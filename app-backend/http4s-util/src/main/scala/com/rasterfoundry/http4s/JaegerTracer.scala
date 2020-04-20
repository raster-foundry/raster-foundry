package com.rasterfoundry.http4s

import cats.effect.Sync
import com.colisweb.tracing.context.OpenTracingContext
import com.colisweb.tracing.core.{TracingContextBuilder, TracingContextResource}
import io.jaegertracing.Configuration
import io.jaegertracing.Configuration._
import io.jaegertracing.internal.{JaegerTracer => JT}
import io.opentracing.Span

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

  def tracingContextBuilder[F[_]: Sync]: TracingContextBuilder[F] = {
    new TracingContextBuilder[F] {

      def build(
          operationName: String,
          tags: Map[String, String] = Map.empty,
          correlationId: String = newCorrelationId
      ): TracingContextResource[F] = {

        val service = tags.getOrElse("service", "raster-foundry")
        OpenTracingContext[F, JT, Span](
          initTracer(service),
          None,
          correlationId
        )(
          operationName,
          tags
        )
      }
    }
  }
}
