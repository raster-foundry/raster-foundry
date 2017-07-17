import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M78 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(78)(List(
    sqlu"""
ALTER TABLE uploads ADD COLUMN scene_cloud_cover REAL;
ALTER TABLE uploads ADD COLUMN scene_acquisition_date TIMESTAMP;
"""
  ))
}
