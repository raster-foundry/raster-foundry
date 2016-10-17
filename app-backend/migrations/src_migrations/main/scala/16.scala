import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M16 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(16)(List(
    sqlu"""
TRUNCATE TABLE footprints;
ALTER TABLE footprints
  ADD COLUMN scene_id UUID REFERENCES scenes(id) NOT NULL;
"""
  ))
}
