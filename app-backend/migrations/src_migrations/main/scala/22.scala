import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M22 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(22)(
    List(
      sqlu"""
ALTER TABLE scenes
  ADD COLUMN metadata_files text[] DEFAULT '{}' NOT NULL;

ALTER TABLE images
  ADD COLUMN metadata_files text[] DEFAULT '{}' NOT NULL;
    """
    ))
}
