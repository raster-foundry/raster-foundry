package com.azavea.rf.api.healthcheck

import com.azavea.rf.database.tables.Users
import com.azavea.rf.database.Database
import com.azavea.rf.api.AkkaSystem
import com.azavea.rf.api.Codec._

import scala.concurrent.ExecutionContext.Implicits.global
import io.circe._
import io.circe.generic.JsonCodec
import de.heikoseeberger.akkahttpcirce.CirceSupport._

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
  * @param status status of raster foundry (e.g. OK, UNHEALTHY)
  * @param services list of individual service checks
  *
  */
@JsonCodec
case class HealthCheck(status: HealthCheckStatus.Status, services: Seq[ServiceCheck])

/**
  * Individual service check for a component (database, cache, etc.)
  *
  * @param service name of service that check is for
  * @param status status of service (e.g. OK, UNHEALTHY)
  */
@JsonCodec
case class ServiceCheck(service: String, status: HealthCheckStatus.Status)

/**
  * Exception for database errors
  *
  * @param description description of error
  */
case class DatabaseException(description:String) extends Exception

object HealthCheckService extends AkkaSystem.LoggerExecutor {

  /**
    * Perform healthcheck by verifying at least the following:
    *   - database is up and users can be queried
    *
    */
  def healthCheck()(implicit database:Database) = {

    log.debug("Healthcheck requested")

    import database.driver.api._

    // Ensure that there is at least one user. This should always be true,
    // because a default user is created in a data migration.
    val countAction = Users.length.result
    database.db.run {
      countAction
    } map {
      case count:Int if count > 0 =>
        log.debug(
          s"Healthcheck returned successfully -- SQL: ${countAction.statements.headOption}"
        )
        HealthCheck(
          HealthCheckStatus.OK,
          Seq(ServiceCheck("database", HealthCheckStatus.OK))
        )
      case _ =>
        log.error(s"Healthcheck failing -- SQL: ${countAction.statements.headOption}")
        HealthCheck(
          HealthCheckStatus.Failing,
          Seq(ServiceCheck("database", HealthCheckStatus.Failing))
        )
    }
  }
}
