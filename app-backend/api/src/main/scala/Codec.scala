package com.rasterfoundry.api

import com.rasterfoundry.api.healthcheck.HealthCheckStatus

import io.circe._

object Codec {
  implicit val healthcheckEncoder: Encoder[HealthCheckStatus.Status] =
    Encoder.encodeString.contramap[HealthCheckStatus.Status](_.toString)
  implicit val healthcheckDecoder: Decoder[HealthCheckStatus.Status] =
    Decoder[String] map { str =>
      HealthCheckStatus.fromString(str)
    }
}
