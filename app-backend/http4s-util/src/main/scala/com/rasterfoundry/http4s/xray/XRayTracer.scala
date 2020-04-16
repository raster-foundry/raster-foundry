package com.rasterfoundry.http4s

import com.rasterfoundry.http4s.xray._

import cats.effect._
import com.amazonaws.services.xray.{AWSXRayAsync, AWSXRayAsyncClientBuilder}
import com.colisweb.tracing.core.{TracingContextBuilder, TracingContextResource}

object XRayTracer {

  def getTracer: AWSXRayAsync = {
    AWSXRayAsyncClientBuilder.defaultClient()
  }

  trait XRayTracingContextBuilder[F[_]] extends TracingContextBuilder[F] {
    def apply(
        operationName: String,
        tags: Map[String, String],
        http: Option[XrayHttp],
        correlationId: String
    ): TracingContextResource[F]

    def build(
        operationName: String,
        tags: Map[String, String],
        correlationId: String = newCorrelationId
    ): TracingContextResource[F]
  }

  def tracingContextBuilder[F[_]: Sync: Timer]: TracingContextBuilder[F] = {

    new XRayTracingContextBuilder[F] {
      def apply(
          operationName: String,
          tags: Map[String, String],
          http: Option[XrayHttp],
          correlationId: String
      ): TracingContextResource[F] = {
        XRayTracingContext[F](getTracer)(operationName, tags)(http)
      }

      def build(
          operationName: String,
          tags: Map[String, String],
          correlationId: String
      ): TracingContextResource[F] = {
        apply(operationName, tags, None, correlationId)
      }
    }
  }
}
