import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M55 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(55)(
    List(
      sqlu"""
CREATE TABLE aois (
  id UUID PRIMARY KEY NOT NULL,
  created_at TIMESTAMP NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  organization_id UUID REFERENCES organizations(id) NOT NULL,
  created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  area geometry(MultiPolygon, 3857) NOT NULL,
  filters JSONB NOT NULL
);
""",
      /* 604,800,000 == One week, in milliseconds */
      sqlu"""
ALTER TABLE projects
  ADD COLUMN is_aoi_project BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN aoi_cadence_millis BIGINT NOT NULL DEFAULT 604800000,
  ADD COLUMN aois_last_checked TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
""",
      sqlu"ALTER TABLE scenes_to_projects ADD COLUMN accepted BOOLEAN NOT NULL DEFAULT TRUE;",
      sqlu"""
CREATE TABLE aois_to_projects (
  aoi_id UUID REFERENCES aois(id),
  project_id UUID REFERENCES projects(id),
  CONSTRAINT aois_to_projects_pkey PRIMARY KEY (aoi_id, project_id)
);
"""
    ))
}
