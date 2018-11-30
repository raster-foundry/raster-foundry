import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M14 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(14)(
    List(
      sqlu"""
CREATE TABLE images (
  id UUID PRIMARY KEY NOT NULL,
  created_at TIMESTAMP NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  organization_id UUID REFERENCES organizations(id) NOT NULL,
  created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  raw_data_bytes INTEGER NOT NULL,
  visibility visibility NOT NULL,
  filename TEXT NOT NULL,
  sourceURI TEXT NOT NULL,
  scene UUID REFERENCES scenes(id) NOT NULL,
  bands text[] NOT NULL,
  image_metadata JSONB NOT NULL
)
"""
    ))
}
