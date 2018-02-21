package com.azavea.rf.api.healthcheck

import cats.free.Free
import com.azavea.rf.api.Codec._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Users
import io.circe.generic.JsonCodec

import scala.concurrent.ExecutionContext.Implicits.global
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.free.connection
import doobie.postgres._
import doobie.postgres.implicits._


/**
  * Available healthcheck values
  */
object HealthCheckStatus extends Enumeration {
  type Status = Value
  val OK, Failing = Value

  def fromString(str: String): Status = str match {
    case "OK" => OK
    case "Failing" => Failing
  }
}

/**
  * Overall healthcheck for Raster Foundry
  *
  * @param status   status of raster foundry (e.g. OK, UNHEALTHY)
  * @param services list of individual service checks
  *
  */
@JsonCodec
case class HealthCheck(status: HealthCheckStatus.Status, services: Seq[ServiceCheck])

/**
  * Individual service check for a component (database, cache, etc.)
  *
  * @param service name of service that check is for
  * @param status  status of service (e.g. OK, UNHEALTHY)
  */
@JsonCodec
case class ServiceCheck(service: String, status: HealthCheckStatus.Status)

/**
  * Exception for database errors
  *
  * @param description description of error
  */
case class DatabaseException(description:String) extends Exception

object HealthCheckService {

  /**
    * Perform healthcheck by verifying at least the following:
    *   - database is up and can make a select query
    *
    */
  def healthCheck(): ConnectionIO[HealthCheck] = {
    val query = sql"SELECT 1".query[Int].unique
    query.map {
      case count: Int if count > 0 =>
        HealthCheck(
          HealthCheckStatus.OK,
          Seq(ServiceCheck("database", HealthCheckStatus.OK))
        )
      case _ =>
        HealthCheck(
          HealthCheckStatus.Failing,
          Seq(ServiceCheck("database", HealthCheckStatus.Failing))
        )
    }
  }
}
