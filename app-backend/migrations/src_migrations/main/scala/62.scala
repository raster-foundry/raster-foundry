import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M62 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(62)(
    List(
      sqlu"""
ALTER TABLE aois_to_projects ADD COLUMN approval_required BOOLEAN DEFAULT true;
ALTER TABLE aois_to_projects ADD COLUMN start_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
"""
    ))
}
