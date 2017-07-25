package com.azavea.rf.api.healthcheck

import com.azavea.rf.api.Codec._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Users
import io.circe.generic.JsonCodec

import scala.concurrent.ExecutionContext.Implicits.global

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
    *   - database is up and users can be queried
    *
    */
  def healthCheck()(implicit database: Database) = {
    import database.driver.api._

    val countAction = Users.take(1).length.result
    database.db.run {
      countAction
    } map {
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
