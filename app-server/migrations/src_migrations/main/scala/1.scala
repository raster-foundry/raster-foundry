import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

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
