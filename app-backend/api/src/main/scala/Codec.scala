package com.azavea.rf.api

import com.azavea.rf.api.healthcheck.HealthCheckStatus

import io.circe._
import com.azavea.rf.common.cache.circe.auto._

object Codec {
  implicit val healthcheckEncoder: Encoder[HealthCheckStatus.Status] =
    Encoder.encodeString.contramap[HealthCheckStatus.Status](_.toString)
  implicit val healthcheckDecoder: Decoder[HealthCheckStatus.Status] =
    Decoder[String] map { str => HealthCheckStatus.fromString(str) }
}
