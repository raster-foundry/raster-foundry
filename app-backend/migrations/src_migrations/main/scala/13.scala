import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M13 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(13)(
    List(
      sqlu"""
CREATE EXTENSION postgis;
CREATE TABLE footprints (
  id UUID PRIMARY KEY NOT NULL,
  organization_id UUID REFERENCES organizations(id) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  multipolygon geometry(MULTIPOLYGON, 3857) NOT NULL
)
"""
    ))
}
