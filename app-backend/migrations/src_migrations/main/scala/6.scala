import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.{SqlMigration, DBIOMigration}

import java.util.{Calendar, Date, UUID}
import java.sql.Timestamp

object M6 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(6)(
    List(
      sqlu"""
INSERT INTO organizations (id, created_at, modified_at, name)
VALUES('dfac6307-b5ef-43f7-beda-b9f208bb7726', now(), now(), 'Public');
""",
      sqlu"""
INSERT INTO users (id, organization_id, auth_id)
VALUES('c871dbc2-4d5e-445e-a7b0-896bf0920859', 'dfac6307-b5ef-43f7-beda-b9f208bb7726', 'default');
"""
    ))
}
