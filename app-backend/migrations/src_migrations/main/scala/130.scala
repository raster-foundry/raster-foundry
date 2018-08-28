import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M130 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(130)(
    List(
      sqlu"""
ALTER TABLE organizations ADD COLUMN visibility visibility DEFAULT 'PRIVATE' NOT NULL;
UPDATE organizations SET visibility = 'PUBLIC' where id in (
  SELECT o.id FROM organizations o JOIN platforms p ON o.id = p.default_organization_id
);
"""
    ))
}
