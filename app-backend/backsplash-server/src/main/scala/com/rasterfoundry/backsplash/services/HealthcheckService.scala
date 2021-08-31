package com.rasterfoundry.backsplash.server

import com.rasterfoundry.database.util.Cache.ProjectLayerCache

import cats._
import cats.data.NonEmptyList
import cats.effect._
import com.typesafe.scalalogging.LazyLogging
import doobie._
import doobie.implicits._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl._
import scalacache.CatsEffect.modes._
import sup.data._
import sup.{mods, Health, HealthCheck, HealthResult}

import scala.concurrent.duration._

class HealthcheckService(xa: Transactor[IO], timeout: FiniteDuration)(
    implicit
    timer: Timer[IO],
    contextShift: ContextShift[IO])
    extends Http4sDsl[IO]
    with LazyLogging {

  val cache = ProjectLayerCache.projectLayerCache

  private def dbHealth =
    HealthCheck
      .liftF[IO, Id] {
        val response = fr"select name from licenses limit 1;"
          .query[String]
          .to[List]
          .transact(xa)
          .attempt
        response.map {
          case Left(e) =>
            logger.error("DB Healthcheck Failed", e)
            throw e
          case _ => HealthResult[Id](Health.Healthy)
        }
      }
      .through(mods.timeoutToSick(timeout))
      .through(mods.tagWith("db"))

  private def cacheHealth =
    HealthCheck
      .liftF[IO, Id] {
        cache.get[IO]("bogus").attempt.map {
          case Left(e) =>
            logger.error("Cache Healthcheck Failed", e)
            throw e
          case _ => HealthResult[Id](Health.Healthy)
        }
      }
      .through(mods.timeoutToSick(timeout))
      .through(mods.tagWith("cache"))

  val routes: HttpRoutes[IO] = HttpRoutes.of {
    case GET -> Root =>
      val healthcheck = HealthReporter.parWrapChecks(
        NonEmptyList.of(dbHealth, cacheHealth)
      )
      healthcheck.check flatMap { result =>
        val report = result.value
        if (report.health == Health.sick) {
          ServiceUnavailable(
            Map(
              "result" -> "sick".asJson,
              "errors" -> report.checks
                .filter(_.health == Health.sick)
                .map(_.tag)
                .asJson
            )
          )
        } else {
          Ok(Map("result" -> "A-ok"))
        }
      }
  }

}
