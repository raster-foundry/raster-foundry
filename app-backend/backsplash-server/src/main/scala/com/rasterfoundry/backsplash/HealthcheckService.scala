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
import org.http4s.dsl.Http4sDsl
import scalacache.modes.sync._
import sup._

import scala.concurrent.duration._

class HealthcheckService(xa: Transactor[IO])(implicit timer: Timer[IO],
                                             contextShift: ContextShift[IO])
    extends Http4sDsl[IO] {

  private def dbHealth: HealthCheck[IO, Id] =
    HealthCheck
      .liftFBoolean(
        // select things from the db
        fr"select name from licenses limit 1;"
          .query[String]
          .to[List]
          .transact(xa)
          .map(_ => true)
      )
      .through(mods.timeoutToSick(3 seconds))

  private def cacheHealth: HealthCheck[IO, Id] =
    HealthCheck
      .liftFBoolean(
        IO { tileCache.get("bogus") } map { _ =>
          true
        }
      )
      .through(mods.timeoutToSick(3 seconds))

  val routes: HttpRoutes[IO] =
    HttpRoutes.of {
      case GET -> Root =>
        // timeout or recover to sick, so that if either the cache / db are reachable but not responding
        // or unreachable, we still return a 503
        val healthcheck = (dbHealth |+| cacheHealth)
          .through(
            mods.timeoutToSick(7 seconds)
          )
          .through(
            mods.recoverToSick
          )
        healthcheck.check flatMap { check =>
          if (check.value.reduce.isHealthy) Ok("A-ok")
          else ServiceUnavailable("No good")
        }
    }
}
