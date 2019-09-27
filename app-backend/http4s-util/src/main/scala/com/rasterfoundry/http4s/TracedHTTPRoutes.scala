package com.rasterfoundry.http4s

import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import cats.implicits._
import com.colisweb.tracing._
import com.colisweb.tracing.TracingContext.TracingContextBuilder
import com.rasterfoundry.datamodel.User
import com.rasterfoundry.http4s.xray.{XrayHttp, XrayRequest}
import io.opentracing._
import org.http4s._
import org.http4s.util.CaseInsensitiveString

object TracedHTTPRoutes {

  def apply[F[_]: Sync](
      pf: PartialFunction[AuthedTraceRequest[F], F[Response[F]]]
  )(
      implicit builder: TracingContextBuilder[F]
  ): Kleisli[OptionT[F, ?], AuthedRequest[F, User], Response[F]] = {
    val tracedRoutes =
      Kleisli[OptionT[F, ?], AuthedTraceRequest[F], Response[F]] { req =>
        pf.andThen(OptionT.liftF(_))
          .applyOrElse(req, Function.const(OptionT.none))
      }
    wrapHttpRoutes(tracedRoutes, builder)
  }

  def wrapHttpRoutes[F[_]: Sync](
      routes: Kleisli[OptionT[F, ?], AuthedTraceRequest[F], Response[F]],
      builder: TracingContextBuilder[F]
  ): Kleisli[OptionT[F, ?], AuthedRequest[F, User], Response[F]] = {
    Kleisli[OptionT[F, ?], AuthedRequest[F, User], Response[F]] { authedReq =>
      val req = authedReq.req
      val operationName = "http4s-request"
      val tags = Map(
        "http_method" -> req.method.name,
        "request_url" -> req.uri.path.toString,
        "environment" -> Config.environment
      ) combine {
        req.headers.get(CaseInsensitiveString("X-Amzn-Trace-Id")) match {
          case Some(header) =>
            header.value.split('=').reverse.headOption match {
              case Some(traceId) => Map("amazon_trace_id" -> traceId)
              case _             => Map.empty[String, String]
            }
          case _ => Map.empty[String, String]
        }
      } combine {
        req.headers.get(CaseInsensitiveString("Referer")) match {
          case Some(referer) => Map("referer" -> referer.value)
          case _             => Map.empty[String, String]
        }
      }

      def transformResponse(
          context: TracingContext[F]): F[Option[Response[F]]] = {
        val tracedRequest = AuthedTraceRequest[F](authedReq, context)
        val responseOptionWithTags = routes.run(tracedRequest) semiflatMap {
          response =>
            val traceTags = Map(
              "http_status" -> response.status.code.toString,
            ) ++ tags ++
              response.headers.toList
                .map(h => (s"response_header_${h.name}" -> h.value))
                .toMap
            context
              .addTags(traceTags)
              .map(_ => response)
        }
        responseOptionWithTags.value
      }

      OptionT {
        builder match {
          case b: XRayTracer.XRayTracingContextBuilder[F] => {
            val request =
              XrayRequest(req.method.name,
                          req.uri.path.toString,
                          req.headers
                            .get(CaseInsensitiveString("User-Agent"))
                            .map(_.toString),
                          req.from.map(_.toString))
            val http = XrayHttp(Some(request), None)
            b(operationName, tags, Some(http)) use (context =>
              transformResponse(context))
          }
          case _ => {
            builder(operationName, tags) use (context =>
              transformResponse(context))
          }
        }
      }
    }
  }

  object using {

    def unapply[F[_], T <: Tracer, S <: Span](tr: AuthedTraceRequest[F])
      : Option[(AuthedRequest[F, User], TracingContext[F])] =
      Some(tr.authedRequest -> tr.tracingContext)
  }
}
