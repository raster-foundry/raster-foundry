import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M41 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(41)(
    List(
      sqlu"""
CREATE TABLE tool_runs (
  id UUID PRIMARY KEY NOT NULL,
  created_at TIMESTAMP NOT NULL,
  created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  visibility visibility NOT NULL,
  organization UUID REFERENCES organizations(id) NOT NULL,
  project UUID REFERENCES projects(id) NOT NULL,
  tool UUID REFERENCES tools(id) NOT NULL,
  execution_parameters JSONB NOT NULL DEFAULT '{}'
);
"""
    ))
}
