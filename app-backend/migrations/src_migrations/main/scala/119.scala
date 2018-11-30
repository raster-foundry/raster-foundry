import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M119 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(119)(
    List(
      sqlu"""
    ALTER TABLE organizations
      ADD COLUMN dropbox_credential TEXT DEFAULT '' NOT NULL,
      ADD COLUMN planet_credential TEXT DEFAULT '' NOT NULL;
    """
    ))
}
