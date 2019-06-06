package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.Cache.tileCache
import cats._
import cats.effect._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import doobie._
import doobie.implicits._
import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.spark.io.s3.S3Client
import org.http4s._
import org.http4s.dsl._
import scalacache.modes.sync._
import sup._

import scala.concurrent.duration._

class HealthcheckService(xa: Transactor[IO])(implicit timer: Timer[IO],
                                             contextShift: ContextShift[IO])
    extends Http4sDsl[IO]
    with LazyLogging {

  private def timeoutToSick(check: HealthCheck[IO, Id]): HealthCheck[IO, Id] =
    HealthCheck
      .race(
        check,
        HealthCheck.liftF[IO, Id](
          IO.sleep(3 seconds) map { _ =>
            HealthResult.one(Health.sick)
          }
        )
      )
      .transform(healthIO =>
        healthIO map {
          case HealthResult(eitherK) =>
            eitherK.run match {
              case (Right(r)) => HealthResult.one(r)
              case (Left(l))  => HealthResult.one(l)
            }
      })

  private def gdalHealth = timeoutToSick(
    HealthCheck.liftF[IO, Id] {
      val s3Client = S3Client.DEFAULT
      val bucket = Config.healthcheck.tiffBucket
      val key = Config.healthcheck.tiffKey
      val s3Path = s"s3://${bucket}/${key}"
      s3Client.doesObjectExist(bucket, key) match {
        case false =>
          logger.warn(s"${s3Path} does not exist - not failing health check")
          IO(HealthResult.one(Health.healthy))
        case true =>
          val rs = GDALRasterSource(s"$s3Path")
          // Read some metadata with GDAL
          val crs = rs.crs
          val bandCount = rs.bandCount
          logger.debug(
            s"Read metadata for ${s3Path} (CRS: ${crs}, Band Count: ${bandCount})")
          IO(HealthResult.one(Health.healthy))
      }
    }
  )

  private def dbHealth =
    timeoutToSick(
      HealthCheck
        .liftF[IO, Id](
          // select things from the db
          fr"select name from licenses limit 1;"
            .query[String]
            .to[List]
            .transact(xa)
            .map(_ => HealthResult.one(Health.healthy))
        )
    )

  private def cacheHealth =
    timeoutToSick(
      HealthCheck
        .liftF[IO, Id](
          IO { tileCache.get("bogus") } map { _ =>
            HealthResult.one(Health.healthy)
          }
        ))

  val routes: HttpRoutes[IO] =
    HttpRoutes.of {
      case GET -> Root =>
        val healthcheck = (dbHealth |+| cacheHealth |+| gdalHealth)
          .through(mods.recoverToSick)
        healthcheck.check flatMap { check =>
          if (check.value.reduce.isHealthy) Ok("A-ok")
          else {
            logger.warn("Healthcheck Failed (postres, memcached, or gdal)")
            ServiceUnavailable("Postgres, memcached, or gdal unavailable")
          }
        }
    }
}
