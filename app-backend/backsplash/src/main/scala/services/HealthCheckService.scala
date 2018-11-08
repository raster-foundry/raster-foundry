package com.rasterfoundry.backsplash.services

import cats.data.OptionT
import cats.effect.Effect
import io.circe.Json
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

class HealthCheckService[F[_]: Effect] extends Http4sDsl[F] {
  val service: HttpRoutes[F] = {
    HttpRoutes.of {
      case GET -> Root => {
        Ok(
          Json.obj("message" -> Json.fromString("Healthy"),
                   "reason" -> Json.fromString("A-ok")))
      }
    }
  }
}
