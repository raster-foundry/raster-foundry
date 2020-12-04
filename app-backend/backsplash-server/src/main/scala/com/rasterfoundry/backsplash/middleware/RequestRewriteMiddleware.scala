package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.database.ProjectDao

import cats.data._
import cats.effect.IO
import doobie.Transactor
import doobie.implicits._
import org.http4s._
import org.http4s.dsl.io._

import java.util.UUID

object RequestRewriteMiddleware {
  private def getDefaultLayerId(
      projectId: UUID,
      xa: Transactor[IO]
  ): OptionT[IO, UUID] =
    OptionT { ProjectDao.getProjectById(projectId).transact(xa) } map {
      _.defaultLayerId
    }

  private def rewrite(
      req: Request[IO],
      projectId: UUID,
      z: Int,
      x: Int,
      y: Int,
      service: HttpRoutes[IO],
      xa: Transactor[IO]
  ) = {
    for {
      defaultLayerId <- getDefaultLayerId(projectId, xa)
      resp <- service(
        req.withPathInfo(
          s"/${projectId}/layers/${defaultLayerId}/${z}/${x}/${y}"
        )
      )
    } yield resp
  }

  def apply(
      service: HttpRoutes[IO],
      xa: Transactor[IO]
  ): Kleisli[OptionT[IO, ?], Request[IO], Response[IO]] =
    Kleisli { req: Request[IO] =>
      {
        // match on the request here and route to the service if it matches the deprecated route
        val scriptName = req.scriptName
        // Due to middleware application order, we have to strip a trailing slash here
        if (req.pathInfo.charAt(req.pathInfo.length - 1) == '/') {
          apply(service, xa)(
            req.withPathInfo(req.pathInfo.substring(0, req.pathInfo.length - 1))
          )
        } else if (scriptName.isEmpty) {
          req match {
            case GET -> Root / "tiles" / UUIDWrapper(projectId) / IntVar(
                  z
                ) / IntVar(x) / IntVar(y) =>
              rewrite(req, projectId, z, x, y, service, xa)
            case GET -> Root / UUIDWrapper(projectId) / IntVar(z) / IntVar(
                  x
                ) / IntVar(y) =>
              rewrite(req, projectId, z, x, y, service, xa)
            case GET -> Root / UUIDWrapper(projectId) / "histogram" =>
              for {
                defaultLayerId <- getDefaultLayerId(projectId, xa)
                resp <- service(
                  req.withPathInfo(
                    s"/${projectId}/layers/${defaultLayerId}/histogram"
                  )
                )
              } yield resp

            case POST -> Root / UUIDWrapper(projectId) / "histogram" =>
              for {
                defaultLayerId <- getDefaultLayerId(projectId, xa)
                resp <- service(
                  req.withPathInfo(
                    s"/${projectId}/layers/${defaultLayerId}/histogram"
                  )
                )
              } yield resp

            case GET -> Root / UUIDWrapper(projectId) / "export" =>
              for {
                defaultLayerId <- getDefaultLayerId(projectId, xa)
                resp <- service(
                  req.withPathInfo(
                    s"/${projectId}/layers/${defaultLayerId}/export"
                  )
                )
              } yield resp

            case _ =>
              service(req)
          }
        } else {
          service(req)
        }
      }
    }
}
