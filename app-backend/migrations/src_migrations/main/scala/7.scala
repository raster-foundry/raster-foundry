import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M7 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(7)(List(
    sqlu"""
ALTER TABLE users
  DROP constraint users_pkey cascade;
ALTER TABLE users
  DROP COLUMN id;
ALTER TABLE users
  RENAME COLUMN auth_id to id;
ALTER TABLE users
  ADD PRIMARY KEY (id);

CREATE TABLE users_to_organizations (
  user_id VARCHAR(255) REFERENCES users(id) NOT NULL,
  organization_id UUID REFERENCES organizations(id) NOT NULL,
  role VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  PRIMARY KEY(user_id, organization_id)
);
ALTER TABLE users
  DROP COLUMN organization_id;
"""
  ))
}
