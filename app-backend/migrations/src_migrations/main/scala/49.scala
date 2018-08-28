import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M49 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(49)(
    List(
      sqlu"""
CREATE TYPE upload_type AS ENUM ('DROPBOX', 'S3', 'LOCAL');

CREATE TYPE upload_status AS ENUM ('CREATED', 'UPLOADING', 'UPLOADED', 'QUEUED', 'PROCESSING', 'COMPLETE');

CREATE TYPE file_type as ENUM ('GEOTIFF', 'GEOTIFF_WITH_METADATA');

CREATE TABLE uploads (
  id UUID PRIMARY KEY NOT NULL,
  created_at TIMESTAMP NOT NULL,
  created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  organization_id UUID REFERENCES organizations(id) NOT NULL,
  upload_status upload_status NOT NULL,
  file_type file_type NOT NULL,
  upload_type upload_type NOT NULL,
  files text[],
  datasource UUID REFERENCES datasources(id) NOT NULL,
  metadata jsonb NOT NULL
);
"""
    ))
}
