import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M5 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(5)(List(
    sqlu"""
DELETE FROM users;
ALTER TABLE users
  ADD auth_id VARCHAR(255) UNIQUE NOT NULL,
  DROP COLUMN created_at,
  DROP COLUMN modified_at,
  DROP COLUMN is_active,
  DROP COLUMN is_staff,
  DROP COLUMN email,
  DROP COLUMN first_name,
  DROP COLUMN last_name;
"""
  ))
}
