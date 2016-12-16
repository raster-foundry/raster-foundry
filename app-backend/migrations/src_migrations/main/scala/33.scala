import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M33 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(33)(List(
    sqlu"""
ALTER TABLE scenes_to_projects ADD COLUMN scene_order integer;
ALTER TABLE scenes_to_projects ADD COLUMN mosaic_definition jsonb;
"""
  ))
}
