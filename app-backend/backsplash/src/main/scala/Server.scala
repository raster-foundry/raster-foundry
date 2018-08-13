package com.azavea.rf.backsplash

import com.azavea.rf.backsplash.services._

import cats.effect.{Effect, IO}
import fs2.StreamApp
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext

object BacksplashServer extends StreamApp[IO] {
  import scala.concurrent.ExecutionContext.Implicits.global

  def stream(args: List[String], requestShutdown: IO[Unit]) = ServerStream.stream[IO]
}

object ServerStream {
  def healthCheckService[F[_]: Effect] = new HealthCheckService[F].service

  def stream[F[_]: Effect](implicit ec: ExecutionContext) =
    BlazeBuilder[F]
      .bindHttp(8080, "0.0.0.0")
      .mountService(healthCheckService, "/")
      .serve
}
