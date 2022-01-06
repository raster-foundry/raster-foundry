package com.rasterfoundry.backsplash.middleware

import com.rasterfoundry.backsplash.server._

import cats.data.Kleisli
import cats.effect.Sync
import com.typesafe.scalalogging.Logger
import io.circe.syntax._
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Header, HttpRoutes, Request}

import java.time.Instant

class AccessLoggingMiddleware[F[_]: Sync](
    service: HttpRoutes[F],
    logger: Logger
) {
  def withLogging(enabled: Boolean) = {
    if (!enabled) service
    else {
      Kleisli { (request: Request[F]) =>
        val requestStart = Instant.now
        val headerWhitelist: Set[CaseInsensitiveString] =
          Set(
            CaseInsensitiveString("user-agent"),
            CaseInsensitiveString("accept-encoding"),
            CaseInsensitiveString("referer"),
            CaseInsensitiveString("origin"),
            CaseInsensitiveString("X-Amzn-Trace-Id")
          )
        val headers =
          Map(
            request.headers.toList.filter(header =>
              headerWhitelist.contains(header.name)) map { header =>
              header.name.toString.toLowerCase -> header.value.asJson
            }: _*
          )
        val requestData =
          Map(
            "method" -> request.method.toString.asJson,
            "uri" -> s"${request.uri}".asJson,
            "httpVersion" -> s"${request.httpVersion}".asJson,
            "requesterIp" -> (request.remote map { _.toString }).asJson,
            "forwardedFor" -> (request.from map { _.toString }).asJson
          ) ++ headers
        service.run(request) map { resp =>
          val requestEnd = Instant.now
          val duration = (requestEnd.toEpochMilli - requestStart.toEpochMilli)
          val responseData = Map(
            "durationInMillis" -> duration.asJson,
            "statusCode" -> resp.status.code.asJson
          )
          val platformIdHeader =
            resp.headers
              .get(CaseInsensitiveString(HeaderPlatIdKey))
              .map(header => Map(HeaderPlatIdKey -> header.value.asJson))
              .getOrElse(Map.empty)
          val platformNameHeader =
            resp.headers
              .get(CaseInsensitiveString(HeaderPlatNameKey))
              .map(header => Map(HeaderPlatNameKey -> header.value.asJson))
              .getOrElse(Map.empty)
          val platformData = platformIdHeader ++ platformNameHeader
          resp
            .putHeaders(Header(HeaderPlatIdKey, ""))
            .putHeaders(Header(HeaderPlatNameKey, ""))
          logger.info(
            (requestData ++ responseData ++ platformData).asJson.noSpaces
          )
          resp
        }
      }
    }
  }
}
