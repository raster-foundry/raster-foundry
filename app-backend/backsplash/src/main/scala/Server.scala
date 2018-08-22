package com.azavea.rf.backsplash

import com.azavea.rf.backsplash.nodes._
import com.azavea.rf.backsplash.services.{HealthCheckService, MultibandMosaicService}

import cats.effect.{Effect, IO}
import fs2.StreamApp
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext.Implicits.global

object BacksplashServer extends StreamApp[IO] {

  def stream(args: List[String], requestShutdown: IO[Unit]) = ServerStream.stream
}

object ServerStream {
  def healthCheckService = new HealthCheckService[IO].service
  def multibandMosaicService = new MultibandMosaicService().service

  def stream =
    BlazeBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .mountService(healthCheckService, "/")
      .mountService(multibandMosaicService, "/mosaic")
      .serve
}
