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

    -- Change smart_layer_id column type to nullable uuid
    ALTER TABLE project_layers
    DROP COLUMN smart_layer_id,
    ADD COLUMN smart_layer_id UUID;

    -- Add a default_layer_id column to projects table and populate with uuid
    -- these default uuid values are temporary
    -- this field is nullable due to similiar reason as default_annotation_group
    ALTER TABLE projects
    ADD COLUMN default_layer_id UUID DEFAULT uuid_generate_v4();

    -- Insert default project layers based on project id and project default layer from projects table
    INSERT INTO project_layers (
      SELECT
        default_layer_id AS id, now() AS created_at, now() AS modified_at, 'Project default layer' AS name, id AS project_id
      FROM projects
    );

    -- default_layer_id of projects table should refer to id of project_layers
    -- remove the default value constraint on default_layer_id
    ALTER TABLE projects
    ADD CONSTRAINT projects_default_project_layer_id_fkey FOREIGN KEY (default_layer_id) REFERENCES project_layers(id),
    ALTER COLUMN default_layer_id DROP DEFAULT;

    -- Create a scenes_to_layers table similar to scenes_to_projects table
    CREATE TABLE scenes_to_layers (
      scene_id              UUID NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
      project_layer_id      UUID NOT NULL REFERENCES project_layers(id) ON DELETE CASCADE,
      scene_order           INTEGER,
      mosaic_definition     JSONB NOT NULL DEFAULT '{}'::json,
      accepted              BOOLEAN NOT NULL DEFAULT true,
      CONSTRAINT scenes_to_layers_pkey PRIMARY KEY (scene_id, project_layer_id)
    );
    """
    ))
}
