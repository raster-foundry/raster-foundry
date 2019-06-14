package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.Cache.tileCache
import cats.effect._
import com.typesafe.scalalogging.LazyLogging
import doobie._
import doobie.implicits._
import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.spark.io.s3.S3Client
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl._
import scalacache.modes.sync._
import sup._
import sup.data.{HealthReporter, Tagged}

import scala.concurrent.duration._

class HealthcheckService(xa: Transactor[IO], quota: Int)(
    implicit timer: Timer[IO],
    contextShift: ContextShift[IO]
) extends Http4sDsl[IO]
    with LazyLogging {

  private def timeoutToSick(
      check: HealthCheck[IO, Tagged[String, ?]],
      failureMessage: String
  ): HealthCheck[IO, Tagged[String, ?]] =
    HealthCheck
      .race(
        check,
        HealthCheck.liftF[IO, Tagged[String, ?]](
          IO.sleep(3 seconds) map { _ =>
            HealthResult.tagged(failureMessage, Health.sick)
          }
        )
      )
      .transform(
        healthIO =>
          healthIO map {
            case HealthResult(eitherK) =>
              eitherK.run match {
                case (Right(r)) => HealthResult(r)
                case (Left(l))  => HealthResult(l)
              }
          }
      )

  private def gdalHealth = timeoutToSick(
    HealthCheck.liftF[IO, Tagged[String, ?]] {
      val s3Client = S3Client.DEFAULT
      val bucket = Config.healthcheck.tiffBucket
      val key = Config.healthcheck.tiffKey
      val s3Path = s"s3://${bucket}/${key}"
      s3Client.doesObjectExist(bucket, key) match {
        case false =>
          logger.warn(s"${s3Path} does not exist - not failing health check")
          IO(HealthResult.tagged("Missing s3 object success", Health.healthy))
        case true =>
          val rs = GDALRasterSource(s"$s3Path")
          // Read some metadata with GDAL
          val crs = rs.crs
          val bandCount = rs.bandCount
          logger.debug(
            s"Read metadata for ${s3Path} (CRS: ${crs}, Band Count: ${bandCount})"
          )
          IO(HealthResult.tagged("Read gdal data successfully", Health.healthy))
      }
    },
    "Could not read data with GDAL"
  )

  private def dbHealth =
    timeoutToSick(
      HealthCheck
        .liftF[IO, Tagged[String, ?]](
          // select things from the db
          fr"select name from licenses limit 1;"
            .query[String]
            .to[List]
            .transact(xa)
            .map(_ => HealthResult.tagged("Db check succeeded", Health.healthy))
        ),
      "Could not read data from database"
    )

  private def cacheHealth =
    timeoutToSick(
      HealthCheck
        .liftF[IO, Tagged[String, ?]](
          IO { tileCache.get("bogus") } map { _ =>
            HealthResult.tagged("Cache read succeeded", Health.healthy)
          }
        ),
      "Could not read data from cache"
    )

  private def totalRequestLimitHealth =
    timeoutToSick(
      {
        val served = Cache.requestCounter.get("requestsServed").getOrElse(0)
        HealthCheck.liftF[IO, Tagged[String, ?]] {
          IO {
            if (served >= quota) {
              val message =
                s"Request quota exceeded -- limit: $quota, counted: $served"
              logger.warn(message)
              HealthResult.tagged(
                message,
                Health.sick
              )
            } else {
              HealthResult.tagged("Healthy", Health.healthy)
            }
          }
        }
      },
      "Could not determine request count"
    )

  val routes: HttpRoutes[IO] =
    HttpRoutes.of {
      case GET -> Root =>
        val healthcheck =
          HealthReporter.fromChecks(
            dbHealth,
            cacheHealth,
            gdalHealth,
            totalRequestLimitHealth
          )
        healthcheck.check flatMap { result =>
          val report = result.value
          // Make all of these `Map[String, Json]` for consistent response shapes
          // There's a sup-http4s module that should do this, but it's on http4s-0.20.0-M4,
          // and there appears to be a binary compatibility issue
          if (report.health == Health.sick) {
            ServiceUnavailable(
              Map(
                "result" -> "Unhealthy".asJson,
                "errors" -> (report.checks
                  .filter(_.health == Health.sick)
                  .map {
                    _.tag
                  })
                  .asJson
              ).asJson
            )
          } else {
            Ok(
              Map(
                "result" -> "A-ok".asJson,
                "errors" -> List.empty[String].asJson
              )
            )
          }
        }
    }
}
