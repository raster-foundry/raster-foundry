package com.rasterfoundry.backsplash

import com.rasterfoundry.backsplash.auth.Authenticators
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.nodes._
import com.rasterfoundry.backsplash.services.{HealthCheckService, MosaicService}
import com.rasterfoundry.backsplash.analysis.AnalysisService
import doobie.util.analysis.Analysis

import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import fs2.StreamApp
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.middleware.AutoSlash

import scala.concurrent.ExecutionContext.Implicits.global

object BacksplashServer extends StreamApp[IO] {

  def stream(args: List[String], requestShutdown: IO[Unit]) =
    ServerStream.stream
}

object ServerStream {

  implicit val timer: Timer[IO] = IO.timer(global)

  def healthCheckService = new HealthCheckService[IO].service
  def mosaicService = new MosaicService().service
  def analysisService = new AnalysisService().service

  def stream =
    BlazeBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .mountService(
        AutoSlash(Authenticators.queryParamAuthMiddleware(mosaicService)),
        "/")
      .mountService(AutoSlash(healthCheckService), "/healthcheck")
      .mountService(
        AutoSlash(Authenticators.queryParamAuthMiddleware(analysisService)),
        "/tools")
      .serve
}
