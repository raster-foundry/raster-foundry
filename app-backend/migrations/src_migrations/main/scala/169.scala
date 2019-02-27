import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M169 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(169)(
    List(
      sqlu"""
ALTER TABLE project_layers
ADD COLUMN
  is_single_band boolean NOT NULL DEFAULT false,
ADD COLUMN
  single_band_options jsonb;

UPDATE project_layers
SET
  is_single_band = projects.is_single_band
FROM projects
WHERE
  projects.id = project_layers.project_id;

UPDATE project_layers
SET
  single_band_options = projects.single_band_options
FROM projects
WHERE
  projects.id = project_layers.project_id;
"""
    ))
}
