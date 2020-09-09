package com.rasterfoundry

import cats.effect._
import com.colisweb.tracing.core.TracingContext
import io.circe.KeyEncoder
import org.http4s.Request
import org.http4s.util.CaseInsensitiveString

package object backsplash {

  type BacksplashMosaic = IO[(TracingContext[IO], List[BacksplashImage[IO]])]

  implicit val encodeKeyDouble: KeyEncoder[Double] = new KeyEncoder[Double] {
    def apply(key: Double): String = key.toString
  }

  /** Helper implicit class to make it make pulling out a trace ID
    * consistent across the code base, if missing an empty string is
    * returned
    *
    * @param req http4s request object
    * @tparam F effect type
    */
  implicit class requestWithTraceID[F[_]](req: Request[F]) {
    def traceID: String = {
      req.headers.get(CaseInsensitiveString("X-Amzn-Trace-Id")) match {
        case Some(s) => s.toString
        case _       => ""
      }
    }
  }
}
