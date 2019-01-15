import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M157 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(157)(
    List(
      sqlu"""
    -- Cascade the deletion of projects so that project layers are also deleted
    ALTER TABLE project_layers
    DROP CONSTRAINT project_layers_project_id_fkey,
    ADD CONSTRAINT project_layers_project_id_fkey FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

    -- Add a default_layer column to projects table and populate with uuid
    ALTER TABLE projects
    ADD COLUMN default_layer UUID NOT NULL DEFAULT uuid_generate_v4();

    -- Insert default project layers based on project id and project default layer from projects table
    INSERT INTO project_layers (
      SELECT
        default_layer AS id, now() AS created_at, now() AS modified_at, 'default_layer' AS name, id AS project_id
      FROM projects
    );

    -- default_layer of projects table should refer to id of project_layers
    ALTER TABLE projects
    ADD CONSTRAINT projects_default_project_layer_id_fkey FOREIGN KEY (default_layer) REFERENCES project_layers(id);

    -- Create a scenes_to_layers table similar to scenes_to_projects table
    CREATE TABLE scenes_to_layers (
      scene_id              UUID NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
      project_layer_id      UUID NOT NULL REFERENCES project_layers(id) ON DELETE CASCADE,
      scene_order           INTEGER,
      mosaic_definition     JSONB NOT NULL DEFAULT '{}'::json,
      accepted              BOOLEAN NOT NULL DEFAULT true,
      CONSTRAINT scenes_to_layers_pkey PRIMARY KEY (scene_id, project_layer_id)
    );

    -- Populate scenes_to_layers table based on the joined result of scenes_to_projects and projects tables
    INSERT INTO scenes_to_layers (
      SELECT
        stp.scene_id, p.default_layer AS project_layer_id, stp.scene_order, stp.mosaic_definition, stp.accepted
      FROM scenes_to_projects AS stp
      JOIN projects p ON stp.project_id = p.id
    );

    -- drop scenes_to_projects table after backend is compatible
    DROP TABLE IF EXISTS scenes_to_projects;
    """
    ))
}
