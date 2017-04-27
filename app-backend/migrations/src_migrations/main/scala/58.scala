import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M58 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(58)(List(
    sqlu"""
ALTER TABLE tool_runs DROP COLUMN project
"""
  ))
}
