import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M110 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(110)(
    List(
      sqlu"""
CREATE TEMPORARY TABLE aois_tmp AS (
  SELECT
    id, created_at, modified_at, organization_id, created_by, modified_by,
    owner, area, filters, is_active, approval_required, start_time, project_id
  FROM
    aois JOIN aois_to_projects on aois.id = aois_to_projects.aoi_id
);

DROP TABLE aois_to_projects;

TRUNCATE TABLE aois;

ALTER TABLE aois
  ADD COLUMN approval_required BOOLEAN DEFAULT false,
  ADD COLUMN start_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  ADD COLUMN project_id uuid NOT NULL,
  ADD CONSTRAINT aoi_to_project_id FOREIGN KEY (project_id) REFERENCES projects(id);

INSERT INTO aois (
  SELECT
    id, created_at, modified_at, organization_id, created_by, modified_by,
    area, filters, owner, is_active, approval_required, start_time, project_id
  FROM aois_tmp
);

"""
    ))
}
