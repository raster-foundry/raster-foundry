import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M9 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(9)(List(
    sqlu"""
CREATE TYPE visibility AS ENUM ('PUBLIC', 'ORGANIZATION', 'PRIVATE');

CREATE TYPE job_status AS ENUM ('UPLOADING', 'SUCCESS', 'FAILURE', 'PARTIALFAILURE', 'QUEUED', 'PROCESSING');

CREATE TABLE scenes (
  id UUID PRIMARY KEY NOT NULL,
  created_at TIMESTAMP NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  organization_id UUID REFERENCES organizations(id) NOT NULL,
  created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  ingest_size_bytes INTEGER NOT NULL,
  visibility visibility NOT NULL,
  resolution_meters REAL NOT NULL,
  tags text[] NOT NULL,
  datasource VARCHAR(255) NOT NULL,
  scene_metadata JSONB NOT NULL,
  cloud_cover REAL,
  acquisition_date TIMESTAMP,
  thumbnail_status job_status NOT NULL,
  boundary_status job_status NOT NULL,
  status job_status NOT NULL
)
    """

  ))
}
