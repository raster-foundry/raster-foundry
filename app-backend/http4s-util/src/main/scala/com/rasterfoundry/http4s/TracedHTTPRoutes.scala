package com.rasterfoundry.http4s

import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import cats.implicits._
import com.colisweb.tracing._
import com.colisweb.tracing.TracingContext.TracingContextBuilder
import com.rasterfoundry.datamodel.User
import io.opentracing._
import io.opentracing.tag.Tags._
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
      val operationName = "http4s-incoming-request"
      val traceHeader =
        req.headers.get(CaseInsensitiveString("X-Amzn-Trace-Id")) match {
          case Some(header) => header.value
          case _            => ""
        }
      val tags = Map(
        HTTP_METHOD.getKey -> req.method.name,
        HTTP_URL.getKey -> req.uri.path.toString,
        "X-Amzn-Trace-Id" -> traceHeader
      )

      OptionT {
        builder(operationName, tags) use { context =>
          val tracedRequest = AuthedTraceRequest[F](authedReq, context)
          val responseOptionWithTags = routes.run(tracedRequest) semiflatMap {
            response =>
              val traceTags = Map(
                HTTP_STATUS.getKey() -> response.status.code.toString
              ) ++ tags ++
                response.headers.toList
                  .map(h => (s"http.response.header.${h.name}" -> h.value))
                  .toMap
              context
                .addTags(traceTags)
                .map(_ => response)
          }
          responseOptionWithTags.value
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
