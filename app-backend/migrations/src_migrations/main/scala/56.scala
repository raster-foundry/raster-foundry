import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M56 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(56)(
    List(
      sqlu"""
CREATE TYPE export_type AS ENUM ('DROPBOX', 'S3', 'LOCAL');
CREATE TYPE export_status AS ENUM ('NOTEXPORTED', 'TOBEEXPORTED', 'EXPORTING', 'EXPORTED', 'FAILED');
CREATE TABLE exports (
  id UUID PRIMARY KEY NOT NULL,
  created_at TIMESTAMP NOT NULL,
  created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  organization_id UUID REFERENCES organizations(id) NOT NULL,
  project_id UUID REFERENCES projects(id) NOT NULL,
  visibility visibility NOT NULL,
  export_status export_status NOT NULL,
  export_type export_type NOT NULL,
  export_options jsonb NOT NULL
);
"""
    ))
}
