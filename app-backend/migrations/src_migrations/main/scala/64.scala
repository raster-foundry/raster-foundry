import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M64 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(64)(
    List(
      sqlu"""
INSERT INTO users (id, organization_id, role, created_at, modified_at, dropbox_credential)
VALUES('default_projects', 'dfac6307-b5ef-43f7-beda-b9f208bb7726', 'VIEWER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '');
"""
    ))
}
