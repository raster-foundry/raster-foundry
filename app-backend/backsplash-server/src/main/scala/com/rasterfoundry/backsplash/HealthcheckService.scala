package com.rasterfoundry.backsplash.server

import cats.data.OptionT
import cats.effect.Effect
import io.circe.Json
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

class HealthcheckService[F[_]: Effect] extends Http4sDsl[F] {
  val routes: HttpRoutes[F] = {
    HttpRoutes.of {
      case GET -> Root => {
        Ok(
          Json.obj("message" -> Json.fromString("Healthy"),
                   "reason" -> Json.fromString("A-ok")))
      }
    }
  }
}
