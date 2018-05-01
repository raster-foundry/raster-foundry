import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

// These migrations cover changes from the doobie refactor of the backend, including adding
// indices and alterations of table definitions for doobie's strict checks of postgresql and
// scala type convertability
object M101 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(101)(
    List(
      // add an index for the scene time lookup
      sqlu"""
  CREATE INDEX IF NOT EXISTS scenes_sort_date on scenes(COALESCE(acquisition_date, created_at));
""",
      // Make approval required not nullable
      sqlu"""
  ALTER TABLE aois_to_projects ALTER COLUMN approval_required SET DEFAULT true;
  ALTER TABLE aois_to_projects ALTER COLUMN approval_required SET NOT NULL;
  ALTER TABLE scenes_to_projects ALTER COLUMN mosaic_definition SET DEFAULT '{}'::json;
  ALTER TABLE scenes_to_projects ALTER COLUMN mosaic_definition SET NOT NULL;
  ALTER TABLE uploads ALTER COLUMN files SET DEFAULT '{}';
  ALTER TABLE uploads ALTER COLUMN files SET NOT NULL;
  ALTER TABLE tool_runs RENAME COLUMN organization TO organization_id;
"""
    )
  )
}
