package com.rasterfoundry.backsplash.middleware

import org.http4s.HttpRoutes
import org.http4s.Request
import cats.data.Kleisli
import io.circe.syntax._
import org.http4s.util.CaseInsensitiveString
import cats.effect.Sync
import com.typesafe.scalalogging.Logger
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
            "httpVersion" -> s"${request.httpVersion}".asJson
          ) ++ headers
        service.run(request) map { resp =>
          val requestEnd = Instant.now
          val duration = (requestEnd.toEpochMilli - requestStart.toEpochMilli)
          val responseData = Map(
            "durationInMillis" -> duration.asJson,
            "statusCode" -> resp.status.code.asJson
          )
          logger.info((requestData ++ responseData).asJson.noSpaces)
          resp
        }
      }
    }
  }
}
