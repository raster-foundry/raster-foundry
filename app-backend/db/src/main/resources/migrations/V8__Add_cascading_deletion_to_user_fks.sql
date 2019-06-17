BEGIN;

ALTER TABLE "access_control_rules"
DROP CONSTRAINT "access_control_rules_created_by_fkey",
ADD CONSTRAINT "access_control_rules_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "annotation_groups"
DROP CONSTRAINT "annotation_groups_created_by_fkey",
DROP CONSTRAINT "annotation_groups_modified_by_fkey",
ADD CONSTRAINT "annotation_groups_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "annotation_groups_modified_by_fkey" FOREIGN KEY (modified_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "annotations"
DROP CONSTRAINT "annotations_created_by_fkey",
DROP CONSTRAINT "annotations_labeled_by_fkey",
DROP CONSTRAINT "annotations_modified_by_fkey",
DROP CONSTRAINT "annotations_verified_by_fkey",
ADD CONSTRAINT "annotations_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "annotations_labeled_by_fkey" FOREIGN KEY (labeled_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "annotations_modified_by_fkey" FOREIGN KEY (modified_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "annotations_verified_by_fkey" FOREIGN KEY (verified_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "aois"
DROP CONSTRAINT "aois_created_by_fkey",
DROP CONSTRAINT "aois_modified_by_fkey",
DROP CONSTRAINT "aois_owner_fkey",
ADD CONSTRAINT "aois_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "aois_modified_by_fkey" FOREIGN KEY (modified_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "datasources"
DROP CONSTRAINT "datasources_created_by_fkey",
DROP CONSTRAINT "datasources_modified_by_fkey",
DROP CONSTRAINT "datasources_owner_fkey",
ADD CONSTRAINT "datasources_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "datasources_modified_by_fkey" FOREIGN KEY (modified_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "exports"
DROP CONSTRAINT "exports_created_by_fkey",
DROP CONSTRAINT "exports_modified_by_fkey",
DROP CONSTRAINT "exports_owner_fkey",
ADD CONSTRAINT "exports_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "exports_modified_by_fkey" FOREIGN KEY (modified_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "images"
DROP CONSTRAINT "images_created_by_fkey",
DROP CONSTRAINT "images_modified_by_fkey",
DROP CONSTRAINT "images_owner_fkey",
ADD CONSTRAINT "images_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "images_modified_by_fkey" FOREIGN KEY (modified_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "map_tokens"
DROP CONSTRAINT "map_tokens_created_by_fkey",
DROP CONSTRAINT "map_tokens_modified_by_fkey",
DROP CONSTRAINT "map_tokens_owner_fkey",
ADD CONSTRAINT "map_tokens_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "map_tokens_modified_by_fkey" FOREIGN KEY (modified_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "projects"
DROP CONSTRAINT "projects_created_by_fkey",
DROP CONSTRAINT "projects_modified_by_fkey",
DROP CONSTRAINT "projects_owner_fkey",
ADD CONSTRAINT "projects_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "projects_modified_by_fkey" FOREIGN KEY (modified_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "scenes"
DROP CONSTRAINT "scenes_created_by_fkey",
DROP CONSTRAINT "scenes_modified_by_fkey",
DROP CONSTRAINT "scenes_owner_fkey",
ADD CONSTRAINT "scenes_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "scenes_modified_by_fkey" FOREIGN KEY (modified_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "shapes"
DROP CONSTRAINT "shapes_created_by_fkey",
DROP CONSTRAINT "shapes_modified_by_fkey",
ADD CONSTRAINT "shapes_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "shapes_modified_by_fkey" FOREIGN KEY (modified_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "task_actions"
DROP CONSTRAINT "task_actions_user_id_fkey",
ADD CONSTRAINT "task_actions_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "tasks"
DROP CONSTRAINT "tasks_created_by_fkey",
DROP CONSTRAINT "tasks_locked_by_fkey",
DROP CONSTRAINT "tasks_owner_fkey",
ADD CONSTRAINT "tasks_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "tasks_locked_by_fkey" FOREIGN KEY (locked_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "tasks_owner_fkey" FOREIGN KEY (owner) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "teams"
DROP CONSTRAINT "teams_created_by_fkey",
DROP CONSTRAINT "teams_modified_by_fkey",
ADD CONSTRAINT "teams_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "teams_modified_by_fkey" FOREIGN KEY (modified_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "tool_runs"
DROP CONSTRAINT "tool_runs_created_by_fkey",
DROP CONSTRAINT "tool_runs_modified_by_fkey",
DROP CONSTRAINT "tool_runs_owner_fkey",
ADD CONSTRAINT "tool_runs_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "tool_runs_modified_by_fkey" FOREIGN KEY (modified_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "tools"
DROP CONSTRAINT "tools_created_by_fkey",
DROP CONSTRAINT "tools_modified_by_fkey",
DROP CONSTRAINT "tools_owner_fkey",
ADD CONSTRAINT "tools_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "tools_modified_by_fkey" FOREIGN KEY (modified_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "uploads"
DROP CONSTRAINT "uploads_created_by_fkey",
DROP CONSTRAINT "uploads_modified_by_fkey",
DROP CONSTRAINT "uploads_owner_fkey",
ADD CONSTRAINT "uploads_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "uploads_modified_by_fkey" FOREIGN KEY (modified_by) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE "user_group_roles"
DROP CONSTRAINT "user_group_roles_created_by_fkey",
DROP CONSTRAINT "user_group_roles_modified_by_fkey",
DROP CONSTRAINT "user_group_roles_user_id_fkey",
ADD CONSTRAINT "user_group_roles_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "user_group_roles_modified_by_fkey" FOREIGN KEY (modified_by) REFERENCES users(id) ON DELETE CASCADE,
ADD CONSTRAINT "user_group_roles_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

COMMIT;
