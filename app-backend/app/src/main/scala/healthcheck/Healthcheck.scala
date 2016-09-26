package com.azavea.rf.healthcheck

import scala.concurrent.ExecutionContext

import com.azavea.rf.datamodel.latest.schema.tables.Users
import com.azavea.rf.utils.Database
import com.azavea.rf.AkkaSystem


/**
  * Available healthcheck values
  */
object HealthCheckStatus extends Enumeration {
  type Status = Value
  val OK, Failing = Value
}


/**
  * Individual service check for a component (database, cache, etc.)
  *
  * @param service name of service that check is for
  * @param status status of service (e.g. OK, UNHEALTHY)
  */
case class ServiceCheck(service: String, status: HealthCheckStatus.Status)


/**
  * Overall healthcheck for Raster Foundry
  *
  * @param status status of raster foundry (e.g. OK, UNHEALTHY)
  * @param services list of individual service checks
  *
  */
case class HealthCheck(status: HealthCheckStatus.Status, services: Seq[ServiceCheck])


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
  def healthCheck()(implicit database:Database, ec:ExecutionContext) = {

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
