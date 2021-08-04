CREATE INDEX IF NOT EXISTS access_control_rules_created_by_idx ON access_control_rules (created_by);

CREATE INDEX IF NOT EXISTS annotation_groups_created_by_idx ON annotation_groups (created_by);

CREATE INDEX IF NOT EXISTS annotation_labels_created_by_idx ON annotation_labels (created_by);

CREATE INDEX IF NOT EXISTS annotations_created_by_idx ON annotations (created_by);

CREATE INDEX IF NOT EXISTS annotations_labeled_by_idx ON annotations (labeled_by);

CREATE INDEX IF NOT EXISTS annotations_verified_by_idx ON annotations (verified_by);

CREATE INDEX IF NOT EXISTS aois_created_by_idx ON aois (created_by);

CREATE INDEX IF NOT EXISTS aois_owner_idx ON aois (owner);

CREATE INDEX IF NOT EXISTS datasources_created_by_idx ON datasources (created_by);

CREATE INDEX IF NOT EXISTS datasources_owner_idx ON datasources (owner);

CREATE INDEX IF NOT EXISTS exports_created_by_idx ON exports (created_by);

CREATE INDEX IF NOT EXISTS exports_owner_idx ON exports (owner);

CREATE INDEX IF NOT EXISTS geojson_uploads_created_by_idx ON geojson_uploads (created_by);

CREATE INDEX IF NOT EXISTS images_created_by_idx ON images (created_by);

CREATE INDEX IF NOT EXISTS images_owner_idx ON images (owner);

CREATE INDEX IF NOT EXISTS map_tokens_created_by_idx ON map_tokens (created_by);

CREATE INDEX IF NOT EXISTS map_tokens_owner_idx ON map_tokens (owner);

CREATE INDEX IF NOT EXISTS projects_created_by_idx ON projects (created_by);

CREATE INDEX IF NOT EXISTS projects_owner_idx ON projects (owner);

CREATE INDEX IF NOT EXISTS scenes_created_by_idx ON scenes (created_by);

CREATE INDEX IF NOT EXISTS scenes_owner_idx ON scenes (owner);

CREATE INDEX IF NOT EXISTS shapes_created_by_idx ON shapes (created_by);

CREATE INDEX IF NOT EXISTS shapes_owner_idx ON shapes (owner);

CREATE INDEX IF NOT EXISTS task_actions_user_id_idx ON task_actions (user_id);

CREATE INDEX IF NOT EXISTS tasks_created_by_idx ON tasks (created_by);

CREATE INDEX IF NOT EXISTS tasks_locked_by_idx ON tasks (locked_by);

CREATE INDEX IF NOT EXISTS tasks_owner_idx ON tasks (owner);

CREATE INDEX IF NOT EXISTS teams_created_by_idx ON teams (created_by);

CREATE INDEX IF NOT EXISTS tool_runs_created_by_idx ON tool_runs (created_by);

CREATE INDEX IF NOT EXISTS tool_runs_owner_idx ON tool_runs (owner);

CREATE INDEX IF NOT EXISTS tools_created_by_idx ON tools (created_by);

CREATE INDEX IF NOT EXISTS tools_owner_idx ON tools (owner);

CREATE INDEX IF NOT EXISTS uploads_created_by_idx ON uploads (created_by);

CREATE INDEX IF NOT EXISTS uploads_owner_idx ON uploads (owner);

CREATE INDEX IF NOT EXISTS user_group_roles_user_id_idx ON user_group_roles (user_id);

CREATE INDEX IF NOT EXISTS user_group_roles_created_by_idx ON user_group_roles (created_by);

ALTER TABLE
    uploads DROP CONSTRAINT uploads_datasource_fkey;

ALTER TABLE
    uploads
ADD
    CONSTRAINT uploads_datasource_fkey FOREIGN KEY (datasource) REFERENCES datasources (id);