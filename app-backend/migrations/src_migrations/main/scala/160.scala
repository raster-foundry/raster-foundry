import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M160 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(160)(
    List(
      sqlu"""
-- make default layers for all projects that don't have them
INSERT INTO project_layers
  (id, created_at, modified_at, name, project_id, color_group_hex)
(
  SELECT uuid_generate_v4(), now(), now(), 'Project default layer', projects.id, '#FFFFFF'
  FROM projects LEFT JOIN project_layers
  ON
    projects.id = project_layers.project_id
  WHERE
    project_layers.project_id IS NULL
);

-- fill in null default layer ids
UPDATE projects
SET default_layer_id =
(SELECT project_layers.id FROM project_layers
 JOIN projects ON project_layers.project_id = projects.id
 WHERE project_layers.name = 'Project default layer')
WHERE
  projects.default_layer_id IS NULL;

ALTER TABLE project_layers ALTER COLUMN project_id DROP NOT NULL;
ALTER TABLE projects ALTER COLUMN default_layer_id SET NOT NULL;
"""
    ))
}
