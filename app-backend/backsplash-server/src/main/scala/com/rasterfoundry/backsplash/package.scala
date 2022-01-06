package com.rasterfoundry.backsplash

import cats.effect._
import geotrellis.raster.summary._
import io.circe.generic.semiauto._
import io.circe.{Encoder, KeyEncoder}
import org.http4s._

import java.util.UUID

package object server {

  // Without this keyencoder we can't encode the bincounts from double histograms
  implicit val encodeKeyDouble: KeyEncoder[Double] = new KeyEncoder[Double] {
    def apply(key: Double): String = key.toString
  }

  implicit val statsEncoder: Encoder[Statistics[Double]] = deriveEncoder

  val HeaderPlatNameKey = "platformName"
  val HeaderPlatIdKey = "platformId"

  implicit class ResponseIOOps(val resp: Response[IO]) extends AnyVal {
    def addTempPlatformInfo(
        platNameOpt: Option[String],
        platIdOpt: Option[UUID]
    ): Response[IO] =
      resp
        .putHeaders(
          Header(
            HeaderPlatNameKey,
            platNameOpt.getOrElse("")
          )
        )
        .putHeaders(
          Header(
            HeaderPlatIdKey,
            platIdOpt.map(_.toString).getOrElse("")
          )
        )
  }
}
