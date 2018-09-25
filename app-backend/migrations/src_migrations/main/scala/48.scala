import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M48 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(48)(
    List(
      sqlu"""
ALTER TABLE users
  ADD COLUMN
    organization_id
      UUID
      DEFAULT 'dfac6307-b5ef-43f7-beda-b9f208bb7726'
      NOT NULL
      REFERENCES organizations(id),
  ADD COLUMN
    role
      VARCHAR(255)
      DEFAULT 'VIEWER'
      NOT NULL,
  ADD COLUMN
    created_at
      TIMESTAMP
      DEFAULT (now() AT TIME ZONE 'utc')
      NOT NULL,
  ADD COLUMN
    modified_at
      TIMESTAMP
      DEFAULT (now() AT TIME ZONE 'utc')
      NOT NULL;


DROP TABLE users_to_organizations;
"""
    ))
}
