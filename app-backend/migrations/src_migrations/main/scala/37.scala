import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M37 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(37)(
    List(
      sqlu"""
CREATE TABLE datasources (
  id UUID PRIMARY KEY NOT NULL,
  created_at TIMESTAMP NOT NULL,
  created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  organization_id UUID REFERENCES organizations(id) NOT NULL,
  name TEXT NOT NULL,
  visibility visibility NOT NULL,
  color_correction JSONB,
  extras JSONB
);
    """
    ))
}
