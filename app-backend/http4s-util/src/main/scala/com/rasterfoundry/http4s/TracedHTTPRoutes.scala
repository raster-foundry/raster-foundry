package com.rasterfoundry.http4s

import com.rasterfoundry.datamodel.User
import com.rasterfoundry.http4s.xray.{XrayHttp, XrayRequest}

import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import cats.implicits._
import com.colisweb.tracing.core.{TracingContext, TracingContextBuilder}
import io.circe.parser._
import io.opentracing._
import org.http4s._
import org.http4s.util.CaseInsensitiveString

import scala.io.Source
import scala.util.Properties

import java.util.UUID

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

  // This will be returned as an http url, e.g.,
  // http://<ip>/v3/<a uuid>
  val metadataLocation = Properties.envOrNone("ECS_CONTAINER_METADATA_URI")
  val instanceMetadataTags: Map[String, String] = (for {
    url <- metadataLocation
    unparsed = Source.fromURL(s"$url/task").mkString
    rawJson <- parse(unparsed).toOption
    obj <- rawJson.asObject
  } yield {
    // the metadata available are largely worthless, except TaskARN
    val tags = obj.toList filter { _._2.isString } flatMap {
      case ("TaskARN", v) => Some(("TaskARN", v.toString.replace("\"", "")))
      case _              => None
    }
    Map(tags: _*)
  }) getOrElse Map.empty

  def getTraceId[F[_]](req: Request[F]): Map[String, String] =
    req.headers.get(CaseInsensitiveString("X-Amzn-Trace-Id")) match {
      case Some(header) =>
        header.value.split('=').reverse.headOption match {
          case Some(traceId) => Map("amazon_trace_id" -> traceId)
          case _             => Map.empty[String, String]
        }
      case _ => Map.empty[String, String]
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
        "request_url" -> req.uri.path,
        "environment" -> Config.environment,
        "user_id" -> authedReq.context.id
      ) combine {
        getTraceId(req)
      } combine {
        req.headers.get(CaseInsensitiveString("Referer")) match {
          case Some(referer) => Map("referer" -> referer.value)
          case _             => Map.empty[String, String]
        }
      } combine { instanceMetadataTags }

      def transformResponse(
          context: TracingContext[F]
      ): F[Option[Response[F]]] = {
        val tracedRequest = AuthedTraceRequest[F](authedReq, context)
        val responseOptionWithTags = routes.run(tracedRequest) semiflatMap {
          response =>
            val traceTags = Map(
              "http_status" -> response.status.code.toString
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
          case b: XRayTracer.XRayTracingContextBuilder[F] =>
            val request =
              XrayRequest(
                req.method.name,
                req.uri.path,
                req.headers
                  .get(CaseInsensitiveString("User-Agent"))
                  .map(_.toString),
                req.from.map(_.toString)
              )
            val http = XrayHttp(Some(request), None)
            val traceIdMap = getTraceId(req)
            b(
              operationName,
              tags,
              Some(http),
              traceIdMap.values.headOption getOrElse s"${UUID.randomUUID}"
            ) use (
                context => transformResponse(context)
            )
          case _ =>
            builder.build(operationName, tags) use (
                context => transformResponse(context)
            )
        }
      }
    }
  }

  object using {

    def unapply[F[_], T <: Tracer, S <: Span](
        tr: AuthedTraceRequest[F]
    ): Option[(AuthedRequest[F, User], TracingContext[F])] =
      Some(tr.authedRequest -> tr.tracingContext)
  }
}
