import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M71 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(71)(List(
    sqlu"""
ALTER TABLE images ALTER COLUMN raw_data_bytes TYPE bigint;
"""
  ))
}
