package com.rasterfoundry.backsplash.utils

import cats.effect._
import org.http4s._

import java.util.UUID

trait ResponseUtils {
  lazy val headerPlatNameKey = "platformName"
  lazy val headerPlatIdKey = "platformId"
  def addTempPlatformInfo(
      resp: Response[IO],
      platNameOpt: Option[String],
      platIdOpt: Option[UUID]
  ): Response[IO] =
    resp
      .putHeaders(
        Header(
          headerPlatNameKey,
          platNameOpt.getOrElse("")
        )
      )
      .putHeaders(
        Header(
          headerPlatIdKey,
          platIdOpt.map(_.toString).getOrElse("")
        )
      )
}
