package com.rasterfoundry.backsplash.server

import com.rasterfoundry.database.MVTLayerDao
import com.rasterfoundry.datamodel.User
import com.rasterfoundry.http4s.TracedHTTPRoutes
import com.rasterfoundry.http4s.TracedHTTPRoutes._

import cats.data.OptionT
import cats.effect._
import cats.implicits._
import com.colisweb.tracing.core.TracingContext
import com.colisweb.tracing.core.TracingContextBuilder
import com.typesafe.scalalogging.LazyLogging
import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.http4s.Header
import org.http4s.Response
import org.http4s.dsl.Http4sDsl

import java.util.UUID

class AnnotationProjectMVTService(xa: Transactor[IO])(
    implicit contextShift: ContextShift[IO],
    builder: TracingContextBuilder[IO]
) extends Http4sDsl[IO]
    with LazyLogging {

  val authorizers = new Authorizers(xa)

  private def getTags(
      annotationProjectId: UUID,
      z: Int,
      x: Int,
      y: Int
  ): Map[String, String] =
    Map(
      "annotationProjectId" -> annotationProjectId.toString,
      "zxy" -> s"$z/$x/$y"
    )

  private def getTile(
      f: (UUID, Int, Int, Int) => ConnectionIO[Option[Array[Byte]]],
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
      respO <- tracingContext.span(
        operationLabel,
        getTags(annotationProjectId, z, x, y)
      ) use { _ =>
        f(annotationProjectId, z, x, y)
          .transact(xa)
      }
      resp <- OptionT {
        respO traverse { byteArray =>
          Ok(
            byteArray,
            Header("content-type", "application/vnd.mapbox-vector-tile")
          )
        }
      } getOrElseF NotFound()
    } yield resp

  val routes =
    TracedHTTPRoutes[IO] {
      case GET -> Root / UUIDVar(annotationProjectId) / "labels" / IntVar(z) / IntVar(
            x
          ) / IntVar(y) as user using context =>
        getTile(
          MVTLayerDao.getAnnotationProjectLabels,
          "get-mvt-labels-byte-array",
          user,
          annotationProjectId,
          z,
          x,
          y,
          context
        )

      case GET -> Root / UUIDVar(annotationProjectId) / "labels" / IntVar(z) / IntVar(
            x
          ) / IntVar(y) as user using context =>
        getTile(
          MVTLayerDao.getAnnotationProjectTasks,
          "get-mvt-tasks-byte-array",
          user,
          annotationProjectId,
          z,
          x,
          y,
          context
        )
    }

}
