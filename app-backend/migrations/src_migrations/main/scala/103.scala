import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M103 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(103)(
    List(
      sqlu"""
ALTER TABLE users ALTER COLUMN dropbox_credential SET DEFAULT '';
UPDATE users SET dropbox_credential = '' WHERE dropbox_credential IS NULL;
ALTER TABLE users ALTER COLUMN dropbox_credential SET NOT NULL;

ALTER TABLE users ALTER COLUMN planet_credential SET DEFAULT '';
UPDATE users SET planet_credential = '' WHERE planet_credential IS NULL;
ALTER TABLE users ALTER COLUMN planet_credential SET NOT NULL;

ALTER TABLE aois_to_projects DROP CONSTRAINT aois_to_projects_aoi_id_fkey;
ALTER TABLE aois_to_projects ADD CONSTRAINT aois_to_projects_aoi_id_fkey FOREIGN KEY (aoi_id) REFERENCES aois(id) ON DELETE CASCADE;
"""
    ))
}
