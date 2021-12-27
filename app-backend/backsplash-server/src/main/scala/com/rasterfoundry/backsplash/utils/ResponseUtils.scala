package com.rasterfoundry.backsplash.utils

import cats.effect._
import org.http4s._

import java.util.UUID

trait ResponseUtils {
  val headerKey = "platformId"
  def addTempPlatformInfo(
      resp: Response[IO],
      platIdOpt: Option[UUID]
  ): Response[IO] =
    resp.putHeaders(
      Header(
        headerKey,
        platIdOpt match {
          case Some(id) => id.toString
          case _        => ""
        }
      )
    )
}
