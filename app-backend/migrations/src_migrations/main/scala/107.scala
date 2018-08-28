import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M107 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(107)(
    List(
      sqlu"""
ALTER TABLE aois ADD COLUMN is_active BOOL NOT NULL DEFAULT false;
"""
    ))
}
