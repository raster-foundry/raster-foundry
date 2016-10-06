import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

/**
  * Schema migration that adds the 'users' table.
  */
object M3 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(3)(List(
    sqlu"""
CREATE TABLE users (
  id UUID PRIMARY KEY NOT NULL,
  created_at TIMESTAMP NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  is_active BOOLEAN DEFAULT TRUE,
  is_staff BOOLEAN DEFAULT FALSE,
  email VARCHAR(255) NOT NULL UNIQUE,
  first_name VARCHAR(255) NOT NULL,
  last_name VARCHAR(255) NOT NULL,
  organization_id UUID REFERENCES organizations(id) NOT NULL
);
"""
  ))
}
