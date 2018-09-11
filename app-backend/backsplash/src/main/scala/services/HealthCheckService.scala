package com.azavea.rf.backsplash.services

import cats.effect.Effect
import io.circe.Json
import org.http4s.HttpService
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

class HealthCheckService[F[_]: Effect] extends Http4sDsl[F] {
  val service: HttpService[F] = {
    HttpService[F] {
      case GET -> Root => {
        Ok(
          Json.obj("message" -> Json.fromString("Healthy"),
                   "reason" -> Json.fromString("A-ok")))
      }
    }
  }
}
