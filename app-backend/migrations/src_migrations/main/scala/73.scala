import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M73 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(73)(List(
    sqlu"""
CREATE INDEX name_idx ON scenes (name);
"""
  ))
}
