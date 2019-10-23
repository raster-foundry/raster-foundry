package com.rasterfoundry.http4s

import cats.effect._
import com.colisweb.tracing.TracingContext._
import com.amazonaws.services.xray.{AWSXRayAsync, AWSXRayAsyncClientBuilder}
import com.rasterfoundry.http4s.xray._

object XRayTracer {

  def getTracer: AWSXRayAsync = {
    AWSXRayAsyncClientBuilder.defaultClient()
  }

  trait XRayTracingContextBuilder[F[_]] extends TracingContextBuilder[F] {
    def apply(operationName: String,
              tags: Map[String, String],
              http: Option[XrayHttp]): TracingContextResource[F]

    def apply(operationName: String,
              tags: Map[String, String]): TracingContextResource[F]
  }

  def tracingContextBuilder[F[_]: Sync: Timer]: TracingContextBuilder[F] = {

    new XRayTracingContextBuilder[F] {
      def apply(operationName: String,
                tags: Map[String, String],
                http: Option[XrayHttp]): TracingContextResource[F] = {
        XRayTracingContext[F](getTracer)(operationName, tags)(http)
      }
      def apply(operationName: String,
                tags: Map[String, String]): TracingContextResource[F] = {
        apply(operationName, tags, None)
      }
    }
  }
}
