import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

/**
  * Schema migration that adds the 'organizations' table.
  *
  * Note: UUID and TIMESTAMP defaults are not yet working in the Slick code generator,
  * which is why they're not configured here.
  */
object M1 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(1)(List(
    sqlu"""
CREATE TABLE organizations (
  id UUID PRIMARY KEY NOT NULL,
  created_at TIMESTAMP NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  name VARCHAR(255) NOT NULL
);
"""
  ))
}
