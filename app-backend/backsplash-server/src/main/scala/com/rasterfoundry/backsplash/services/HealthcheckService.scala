package com.rasterfoundry.backsplash.server

import cats._
import cats.data.NonEmptyList
import cats.effect._
import com.rasterfoundry.backsplash.Cache.tileCache
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
import sup.data._
import sup.{Health, HealthCheck, HealthResult, mods}

import scala.concurrent.duration._

class HealthcheckService(xa: Transactor[IO], quota: Int)(
    implicit timer: Timer[IO],
    contextShift: ContextShift[IO]
) extends Http4sDsl[IO]
    with LazyLogging {

  val s3Client = S3Client.DEFAULT
  val bucket = Config.healthcheck.tiffBucket
  val key = Config.healthcheck.tiffKey
  val s3Path = s"s3://${bucket}/${key}"

  private def gdalHealth =
    HealthCheck
      .liftF[IO, Id](
        IO {
          s3Client.doesObjectExist(bucket, key) match {
            case false =>
              logger.warn(
                s"${s3Path} does not exist - not failing health check")
              HealthResult[Id](Health.Healthy)
            case true =>
              val rs = GDALRasterSource(s"$s3Path")
              rs.crs
              rs.extent
              rs.bandCount
          }
        } map { _ =>
          HealthResult[Id](Health.Healthy)
        }
      )
      .through(mods.timeoutToSick(3 seconds))
      .through(mods.tagWith("gdal"))

  private def dbHealth =
    HealthCheck
      .liftF[IO, Id] {
        fr"select name from licenses limit 1;"
          .query[String]
          .to[List]
          .transact(xa)
          .map(_ => HealthResult[Id](Health.Healthy))
      }
      .through(mods.timeoutToSick(3 seconds))
      .through(mods.tagWith("db"))

  private def cacheHealth =
    HealthCheck
      .liftF[IO, Id](IO(tileCache.get("bogus")).map(_ =>
        HealthResult[Id](Health.Healthy)))
      .through(mods.timeoutToSick(3 seconds))
      .through(mods.tagWith("cache"))

  private def totalRequestLimitHealth =
    HealthCheck
      .liftF[IO, Id] {
        IO {
          val served = Cache.requestCounter.get("requestsServed").getOrElse(0)
          if (quota == 0) {
            val message =
              "Ignoring request quota check - configured value is 0"
            logger.debug(message)
            HealthResult[Id](Health.Healthy)
          } else if (served >= quota) {
            val message =
              s"Request quota exceeded -- limit: $quota, counted: $served"
            logger.warn(message)
            HealthResult[Id](Health.Sick)
          } else {
            HealthResult[Id](Health.Healthy)
          }
        }
      }
      .through(mods.timeoutToSick(3 seconds))
      .through(mods.recoverToSick)
      .through(mods.tagWith("requestQuota"))

  val routes: HttpRoutes[IO] = HttpRoutes.of {
    case GET -> Root =>
      val healthcheck = HealthReporter.parWrapChecks(
        NonEmptyList.of(dbHealth,
                        cacheHealth,
                        gdalHealth,
                        totalRequestLimitHealth)
      )
      healthcheck.check flatMap { result =>
        val report = result.value
        if (report.health == Health.sick) {
          ServiceUnavailable(
            Map("result" -> "sick".asJson,
                "errors" -> report.checks
                  .filter(_.health == Health.sick)
                  .map(_.tag)
                  .asJson)
          )
        } else {
          Ok(Map("result" -> "A-ok"))
        }
      }
  }

}
