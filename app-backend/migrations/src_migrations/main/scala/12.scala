import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M12 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(12)(List(
    sqlu"""
ALTER TABLE scenes
    ADD COLUMN sun_azimuth REAL,
    ADD COLUMN sun_elevation REAL;
"""
  ))
}
