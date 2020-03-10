package com.rasterfoundry.http4s.xray

import cats.data._
import cats.effect.concurrent.Ref
import cats.effect.{Resource, Sync, Timer}
import cats.implicits._
import com.amazonaws.services.xray.AWSXRayAsync
import com.amazonaws.xray.entities.TraceID
import com.colisweb.tracing.TracingContext
import com.colisweb.tracing.TracingContext.TracingContextResource

import scala.util.Random

import java.time.Instant

class XRayTracingContext[F[_]: Sync: Timer](
    client: AWSXRayAsync,
    val segment: Segment[F],
    val tagsRef: Ref[F, Map[String, String]])
    extends TracingContext[F] {

  override def spanId: OptionT[F, String] = OptionT.pure(segment.id)
  override def traceId: OptionT[F, String] = OptionT.pure(segment.trace_id)

  def addTags(tags: Map[String, String]): F[Unit] = {
    tagsRef.update(_ ++ tags)
  }

  def childSpan(
      operationName: String,
      tags: Map[String, String]
  ): TracingContextResource[F] = {
    val sanitize: String => String = (s: String) => s.replaceAll("[(,)]", "_")
    XRayTracingContext(client, Some(this))(sanitize(operationName), tags)(None)
  }
}

object XRayTracingContext {

  def apply[F[_]: Sync: Timer](client: AWSXRayAsync,
                               parentContext: Option[XRayTracingContext[F]] =
                                 None)(
      operationName: String,
      tags: Map[String, String]
  )(http: Option[XrayHttp]): TracingContextResource[F] = {
    resource(client, parentContext, operationName, tags, http).evalMap(ctx =>
      ctx.addTags(tags).map(_ => ctx))
  }

  private def resource[F[_]: Sync: Timer](
      client: AWSXRayAsync,
      parentContext: Option[XRayTracingContext[F]],
      operationName: String,
      tags: Map[String, String],
      http: Option[XrayHttp]): TracingContextResource[F] = {

    val acquire: F[(Segment[F], Ref[F, Map[String, String]])] = {
      val spanId = f"${Random.nextLong()}%016x"
      for {
        start <- Sync[F].delay(Instant.now.toEpochMilli.toDouble / 1000)
        tagsRef <- Ref[F].of(tags)
        tags <- tagsRef.get
        segment = {
          // If there is a parent context, that means this segment is a
          // subsegment
          parentContext match {
            case Some(context) =>
              Segment[F](operationName,
                         spanId,
                         context.segment.trace_id,
                         start,
                         None,
                         Some(true),
                         Some(context.segment.id),
                         tags,
                         Some("subsegment"),
                         http)
            case _ =>
              Segment[F](
                operationName,
                spanId,
                tags.getOrElse("amazon_trace_id", new TraceID().toString),
                start,
                None,
                Some(true),
                None,
                tags,
                None,
                http)
          }
        }
        _ <- UdpClient.write(segment).attempt
      } yield (segment, tagsRef)
    }

    def release(input: (Segment[F], Ref[F, Map[String, String]])): F[Unit] = {
      val (segment, tagRef) = input
      for {
        end <- Sync[F].delay(Instant.now.toEpochMilli.toDouble / 1000)
        tags <- tagRef.get
        updatedSegment = {
          segment
            .copy[F](end_time = Some(end),
                     in_progress = None,
                     annotations = tags)
        }
        _ <- UdpClient.write(updatedSegment).attempt
      } yield ()
    }
    Resource
      .make(acquire)(release)
      .map {
        case (segment, tags) => new XRayTracingContext[F](client, segment, tags)
      }
  }
}
