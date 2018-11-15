package com.rasterfoundry.backsplash

import com.rasterfoundry.backsplash.auth.Authenticators
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.nodes._
import com.rasterfoundry.backsplash.services.{HealthCheckService, MosaicService}
import com.rasterfoundry.backsplash.analysis.AnalysisService

import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import com.olegpy.meow.hierarchy._
import fs2._
import org.http4s._
import org.http4s.server.Router
import org.http4s.server.middleware.{CORS, CORSConfig}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.AutoSlash
import org.http4s.syntax.kleisli._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object BacksplashServer extends IOApp {

  implicit val backsplashErrorHandler
    : HttpErrorHandler[IO, BacksplashException] =
    new BacksplashHttpErrorHandler[IO]

  implicit val foreignErrorHandler: HttpErrorHandler[IO, Throwable] =
    new ForeignErrorHandler[IO, Throwable]

  def withCORS(svc: HttpRoutes[IO]): HttpRoutes[IO] =
    CORS(
      svc,
      CORSConfig(
        anyOrigin = true,
        anyMethod = false,
        allowedMethods = Some(Set("GET", "POST", "HEAD", "OPTIONS")),
        allowedHeaders = Some(Set("Content-Type", "Authorization", "*")),
        allowCredentials = true,
        maxAge = 1800
      )
    )

  def healthCheckService = new HealthCheckService[IO].service
  def analysisService =
    Authenticators.tokensAuthMiddleware(new AnalysisService().service)
  def mosaicService =
    Authenticators.tokensAuthMiddleware(new MosaicService().service)

  val httpApp =
    Router(
      "/" -> AutoSlash(withCORS(mosaicService)),
      "/healthcheck" -> AutoSlash(healthCheckService),
      "/tools" -> AutoSlash(withCORS(analysisService))
    )

  def stream =
    BlazeServerBuilder[IO]
      .enableHttp2(true)
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(httpApp.orNotFound)
      .serve

  def run(args: List[String]): IO[ExitCode] =
    stream.compile.drain.as(ExitCode.Success)
}
