import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M34 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(34)(
    List(
      sqlu"""
ALTER TABLE projects ADD COLUMN manual_order boolean DEFAULT true;
ALTER TABLE scenes_to_projects ADD COLUMN scene_order integer;
ALTER TABLE scenes_to_projects ADD COLUMN mosaic_definition jsonb;
    """
    ))
}
