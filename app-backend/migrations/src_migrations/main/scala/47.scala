import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M47 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(47)(
    List(
      sqlu"""
CREATE TABLE map_tokens (
  id UUID PRIMARY KEY NOT NULL,
  created_at TIMESTAMP NOT NULL,
  created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  organization_id UUID REFERENCES organizations(id) NOT NULL,
  project_id UUID REFERENCES projects(id) NOT NULL,
  name TEXT
);
"""
    ))
}
