package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.Cache.tileCache

import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import doobie._
import doobie.implicits._
import io.circe.Json
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl._
import scalacache.modes.sync._
import sup._

import scala.concurrent.duration._

class HealthcheckService(xa: Transactor[IO])(implicit timer: Timer[IO],
                                             contextShift: ContextShift[IO])
    extends Http4sDsl[IO] {

  private def timeoutToSick(check: HealthCheck[IO, Id]): HealthCheck[IO, Id] =
    HealthCheck
      .race(
        check,
        HealthCheck.liftF[IO, Id](
          IO.sleep(3 seconds) map { _ =>
            HealthResult.one(Health.sick)
          }
        )
      )
      .transform(healthIO =>
        healthIO map {
          case HealthResult(eitherK) =>
            eitherK.run match {
              case (Right(r)) => HealthResult.one(r)
              case (Left(l))  => HealthResult.one(l)
            }
      })

  private def dbHealth =
    timeoutToSick(
      HealthCheck
        .liftF[IO, Id](
          // select things from the db
          fr"select name from licenses limit 1;"
            .query[String]
            .to[List]
            .transact(xa)
            .map(_ => HealthResult.one(Health.healthy))
        )
    )

  private def cacheHealth =
    timeoutToSick(
      HealthCheck
        .liftF[IO, Id](
          IO { tileCache.get("bogus") } map { _ =>
            HealthResult.one(Health.healthy)
          }
        ))

  val routes: HttpRoutes[IO] =
    HttpRoutes.of {
      case GET -> Root =>
        val healthcheck = (dbHealth |+| cacheHealth)
          .through(mods.recoverToSick)
        healthcheck.check flatMap { check =>
          if (check.value.reduce.isHealthy) Ok("A-ok")
          else {
            ServiceUnavailable("Postgres or memcached unavailable")
          }
        }
    }
}
