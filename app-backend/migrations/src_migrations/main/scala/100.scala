import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M100 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(100)(
    List(
      sqlu"""

ALTER TABLE
 annotations
ALTER COLUMN
 owner
SET
 NOT NULL;

ALTER TABLE
 scenes
ALTER COLUMN
 datasource
SET
 NOT NULL;

ALTER TABLE
 projects
ALTER COLUMN
 manual_order
SET
 NOT NULL;

ALTER TABLE
 projects
ALTER COLUMN
 is_single_band
SET
 NOT NULL;

ALTER TABLE
 projects
ALTER COLUMN
 is_single_band
SET
 NOT NULL;

ALTER TABLE
 users
ALTER COLUMN
 email_notifications
SET
 NOT NULL;

ALTER TABLE
 datasources
ALTER COLUMN
 extras
SET DEFAULT
 '{}'::jsonb;

ALTER TABLE
 datasources
ALTER COLUMN
 extras
SET
 NOT NULL;

ALTER TABLE
 datasources
ALTER COLUMN
 composites
SET
 NOT NULL;

ALTER TABLE
 datasources
ALTER COLUMN
 bands
SET
 NOT NULL;

ALTER TABLE
 map_tokens
ALTER COLUMN
 name
SET
 NOT NULL;

ALTER TABLE
 shapes
ALTER COLUMN
 owner
SET
 NOT NULL;
""" // your sql code goes here
    ))
}
