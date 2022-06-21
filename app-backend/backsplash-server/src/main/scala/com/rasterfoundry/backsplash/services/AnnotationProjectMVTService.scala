package com.rasterfoundry.backsplash.server

import com.rasterfoundry.database.MVTLayerDao
import com.rasterfoundry.datamodel.User
import com.rasterfoundry.http4s.TracedHTTPRoutes
import com.rasterfoundry.http4s.TracedHTTPRoutes._

import cats.effect._
import com.colisweb.tracing.core.TracingContext
import com.colisweb.tracing.core.TracingContextBuilder
import com.typesafe.scalalogging.LazyLogging
import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.http4s.CacheDirective.{`max-age`, `no-cache`}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Cache-Control`
import org.http4s.{Header, Response}

import scala.concurrent.duration._

import java.util.UUID

class AnnotationProjectMVTService(xa: Transactor[IO])(
    implicit
    contextShift: ContextShift[IO],
    builder: TracingContextBuilder[IO])
    extends Http4sDsl[IO]
    with LazyLogging {

  val authorizers = new Authorizers(xa)

  private def noCache(resp: Response[IO]): Response[IO] =
    resp.putHeaders(
      Header(`Cache-Control`.name.toString, `no-cache`.toString)
    )

  private def shortCache(resp: Response[IO]): Response[IO] =
    resp.putHeaders(
      Header(`Cache-Control`.name.toString, `max-age`(60 seconds).toString)
    )

  private def getTags(
      annotationProjectId: UUID,
      z: Int,
      x: Int,
      y: Int,
      hitlVersionIdO: Option[UUID] = None
  ): Map[String, String] = {
    val tags = Map(
      "annotationProjectId" -> annotationProjectId.toString,
      "zxy" -> s"$z/$x/$y",
      "project-tms-triple" -> s"$annotationProjectId-$z-$x-$y"
    )
    hitlVersionIdO match {
      case Some(hitlVersionId) =>
        tags + ("hitlVersionId" -> hitlVersionId.toString)
      case _ =>
        tags
    }
  }

  private def getTile(
      f: (UUID, Int, Int, Int) => ConnectionIO[Array[Byte]],
      operationLabel: String,
      user: User,
      annotationProjectId: UUID,
      z: Int,
      x: Int,
      y: Int,
      tracingContext: TracingContext[IO]
  ): IO[Response[IO]] =
    for {
      _ <- authorizers.authAnnotationProject(
        user,
        annotationProjectId,
        tracingContext
      )
      byteArray <- tracingContext.span(
        operationLabel,
        getTags(annotationProjectId, z, x, y)
      ) use { _ =>
        f(annotationProjectId, z, x, y)
          .transact(xa)
      }
      resp <- Ok(
        byteArray,
        Header("content-type", "application/vnd.mapbox-vector-tile")
      )
    } yield resp

  val routes =
    TracedHTTPRoutes[IO] {
      case GET -> Root / UUIDVar(annotationProjectId) / "labels" / IntVar(
            z
          ) / IntVar(
            x
          ) / IntVar(y) as user using context =>
        getTile(
          MVTLayerDao.getAnnotationProjectLabels,
          "get-mvt-labels-byte-array",
          user.toUser,
          annotationProjectId,
          z,
          x,
          y,
          context
        ) map { shortCache } map {
          _.addTempPlatformInfo(user.platformNameOpt, user.platformIdOpt)
        }

      case GET -> Root / UUIDVar(annotationProjectId) / "tasks" / IntVar(
            z
          ) / IntVar(
            x
          ) / IntVar(y) as user using context =>
        getTile(
          MVTLayerDao.getAnnotationProjectTasks,
          "get-mvt-tasks-byte-array",
          user.toUser,
          annotationProjectId,
          z,
          x,
          y,
          context
        ) map { noCache } map {
          _.addTempPlatformInfo(user.platformNameOpt, user.platformIdOpt)
        }

      case GET -> Root / UUIDVar(annotationProjectId) / "hitl-labels" / UUIDVar(
            hitlVersionId
          ) / IntVar(
            z
          ) / IntVar(
            x
          ) / IntVar(y) as user using context =>
        (for {
          _ <- authorizers.authAnnotationProject(
            user.toUser,
            annotationProjectId,
            context
          )
          byteArray <- context.span(
            "get-mvt-hitl-labels-byte-array",
            getTags(annotationProjectId, z, x, y, Some(hitlVersionId))
          ) use { _ =>
            MVTLayerDao
              .getAnnotationProjectHITLLabels(
                annotationProjectId,
                z,
                x,
                y,
                hitlVersionId
              )
              .transact(xa)
          }
          resp <- Ok(
            byteArray,
            Header("content-type", "application/vnd.mapbox-vector-tile")
          )
        } yield resp) map { shortCache } map {
          _.addTempPlatformInfo(user.platformNameOpt, user.platformIdOpt)
        }
    }

}
