import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M11 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(11)(List(
    sqlu"""
CREATE TYPE thumbnailsize AS ENUM ('small', 'large', 'square');

CREATE TABLE thumbnails (
  id UUID PRIMARY KEY NOT NULL,
  created_at TIMESTAMP NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  organization_id UUID REFERENCES organizations(id) NOT NULL,
  width_px INTEGER NOT NULL,
  height_px INTEGER NOT NULL,
  size THUMBNAILSIZE NOT NULL,
  scene UUID REFERENCES scenes(id) NOT NULL,
  url VARCHAR(255) NOT NULL
);
"""
  ))
}
