import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M129 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(129)(
    List(
      sqlu"""
CREATE TYPE membership_status AS ENUM ('REQUESTED', 'INVITED', 'APPROVED');

ALTER TABLE user_group_roles
ADD COLUMN membership_status membership_status NOT NULL DEFAULT 'APPROVED';
"""
    ))
}
