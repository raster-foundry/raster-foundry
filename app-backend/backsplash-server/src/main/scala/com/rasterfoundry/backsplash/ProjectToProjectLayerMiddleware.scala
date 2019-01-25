package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.Parameters.{
  BandOverrideQueryParamDecoder,
  UUIDWrapper
}
import com.rasterfoundry.database.ProjectDao

import cats.data._
import cats.effect.IO
import doobie.Transactor
import doobie.implicits._

import org.http4s._
import org.http4s.dsl.io._

object ProjectToProjectLayerMiddleware {
  def apply(
      service: HttpRoutes[IO],
      xa: Transactor[IO]): Kleisli[OptionT[IO, ?], Request[IO], Response[IO]] =
    Kleisli { req: Request[IO] =>
      {
        // match on the request here and route to the service if it matches the deprecated route
        val scriptName = req.scriptName
        // Due to middleware application order, we have to strip a trailing slash here
        if (scriptName.isEmpty && req.pathInfo.charAt(req.pathInfo.length - 1) == '/') {
          apply(service, xa)(
            req.withPathInfo(
              req.pathInfo.substring(0, req.pathInfo.length - 1)))
        } else if (scriptName.isEmpty) {
          req match {
            case GET -> Root / UUIDWrapper(projectId) / IntVar(z) / IntVar(x) / IntVar(
                  y)
                  :? BandOverrideQueryParamDecoder(bandOverride) =>
              for {
                defaultLayerId <- OptionT {
                  ProjectDao
                    .getProjectById(projectId)
                    .transact(xa)
                } map { _.defaultLayerId }
                resp <- service(
                  req.withPathInfo(
                    s"/${projectId}/layers/${defaultLayerId}/${z}/${x}/${y}"))
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
