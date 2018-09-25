import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M132 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(132)(
    List(
      sqlu"""
CREATE TYPE org_status AS ENUM ('INACTIVE', 'REQUESTED', 'ACTIVE');
ALTER TABLE organizations ADD COLUMN status org_status;
UPDATE organizations SET status = 'ACTIVE'::org_status WHERE is_active = 'true';
UPDATE organizations set status = 'INACTIVE'::org_status WHERE is_active = 'false';
ALTER TABLE organizations ALTER COLUMN status SET NOT NULL;
ALTER TABLE organizations DROP COLUMN is_active;
"""
    ))
}
