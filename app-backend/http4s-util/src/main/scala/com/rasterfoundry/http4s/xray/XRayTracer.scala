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

  def tracingContextBuilder(implicit contextShift: ContextShift[IO],
                            timer: Timer[IO]): TracingContextBuilder[IO] = {

    new XRayTracingContextBuilder[IO] {
      def apply(operationName: String,
                tags: Map[String, String],
                http: Option[XrayHttp]): TracingContextResource[IO] = {
        XRayTracingContext[IO](getTracer)(operationName, tags)(http)
      }
      def apply(operationName: String,
                tags: Map[String, String]): TracingContextResource[IO] = {
        apply(operationName, tags, None)
      }
    }
  }
}
