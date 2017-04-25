import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M57 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(57)(List(
    sqlu"""
ALTER TABLE tool_runs DROP COLUMN project
"""
  ))
}
