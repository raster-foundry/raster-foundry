BEGIN;

ALTER TABLE "access_control_rules"
DROP CONSTRAINT "access_control_rules_created_by_fkey",
ADD CONSTRAINT "access_control_rules_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE "annotation_groups"
DROP COLUMN "modified_by",
DROP CONSTRAINT "annotation_groups_created_by_fkey",
ADD CONSTRAINT "annotation_groups_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE "annotations"
DROP COLUMN "modified_by",
DROP CONSTRAINT "annotations_created_by_fkey",
DROP CONSTRAINT "annotations_labeled_by_fkey",
DROP CONSTRAINT "annotations_verified_by_fkey",
ADD CONSTRAINT "annotations_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID,
ADD CONSTRAINT "annotations_labeled_by_fkey" FOREIGN KEY (labeled_by) REFERENCES users(id) ON DELETE SET NULL NOT VALID,
ADD CONSTRAINT "annotations_verified_by_fkey" FOREIGN KEY (verified_by) REFERENCES users(id) ON DELETE SET NULL NOT VALID;

ALTER TABLE "aois"
DROP COLUMN "modified_by",
DROP CONSTRAINT "aois_created_by_fkey",
DROP CONSTRAINT "aois_owner_fkey",
ADD CONSTRAINT "aois_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID,
ADD CONSTRAINT "aois_owner_fkey" FOREIGN KEY (owner) REFERENCES users(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE "datasources"
DROP COLUMN "modified_by",
DROP CONSTRAINT "datasources_created_by_fkey",
DROP CONSTRAINT "datasources_owner_fkey",
ADD CONSTRAINT "datasources_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID,
ADD CONSTRAINT "datasources_owner_fkey" FOREIGN KEY (owner) REFERENCES users(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE "exports"
DROP COLUMN "modified_by",
DROP CONSTRAINT "exports_created_by_fkey",
DROP CONSTRAINT "exports_owner_fkey",
ADD CONSTRAINT "exports_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID,
ADD CONSTRAINT "exports_owner_fkey" FOREIGN KEY (owner) REFERENCES users(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE "images"
DROP COLUMN "modified_by",
DROP CONSTRAINT "images_created_by_fkey",
DROP CONSTRAINT "images_owner_fkey",
ADD CONSTRAINT "images_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID,
ADD CONSTRAINT "images_owner_fkey" FOREIGN KEY (owner) REFERENCES users(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE "map_tokens"
DROP COLUMN "modified_by",
DROP CONSTRAINT "map_tokens_created_by_fkey",
DROP CONSTRAINT "map_tokens_owner_fkey",
ADD CONSTRAINT "map_tokens_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID,
ADD CONSTRAINT "map_tokens_owner_fkey" FOREIGN KEY (owner) REFERENCES users(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE "projects"
DROP COLUMN "modified_by",
DROP CONSTRAINT "projects_created_by_fkey",
DROP CONSTRAINT "projects_owner_fkey",
ADD CONSTRAINT "projects_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID,
ADD CONSTRAINT "projects_owner_fkey" FOREIGN KEY (owner) REFERENCES users(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE "scenes"
DROP COLUMN "modified_by",
DROP CONSTRAINT "scenes_created_by_fkey",
DROP CONSTRAINT "scenes_owner_fkey",
ADD CONSTRAINT "scenes_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID,
ADD CONSTRAINT "scenes_owner_fkey" FOREIGN KEY (owner) REFERENCES users(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE "shapes"
DROP COLUMN "modified_by",
DROP CONSTRAINT "shapes_created_by_fkey",
ADD CONSTRAINT "shapes_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE "task_actions"
DROP CONSTRAINT "task_actions_user_id_fkey",
ADD CONSTRAINT "task_actions_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL NOT VALID;

ALTER TABLE "tasks"
DROP COLUMN "modified_by",
DROP CONSTRAINT "tasks_created_by_fkey",
DROP CONSTRAINT "tasks_locked_by_fkey",
DROP CONSTRAINT "tasks_owner_fkey",
ADD CONSTRAINT "tasks_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID,
ADD CONSTRAINT "tasks_locked_by_fkey" FOREIGN KEY (locked_by) REFERENCES users(id) ON DELETE SET NULL NOT VALID,
ADD CONSTRAINT "tasks_owner_fkey" FOREIGN KEY (owner) REFERENCES users(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE "teams"
DROP COLUMN "modified_by",
DROP CONSTRAINT "teams_created_by_fkey",
ADD CONSTRAINT "teams_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE "tool_runs"
DROP COLUMN "modified_by",
DROP CONSTRAINT "tool_runs_created_by_fkey",
DROP CONSTRAINT "tool_runs_owner_fkey",
ADD CONSTRAINT "tool_runs_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID,
ADD CONSTRAINT "tool_runs_owner_fkey" FOREIGN KEY (owner) REFERENCES users(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE "tools"
DROP COLUMN "modified_by",
DROP CONSTRAINT "tools_created_by_fkey",
DROP CONSTRAINT "tools_owner_fkey",
ADD CONSTRAINT "tools_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID,
ADD CONSTRAINT "tools_owner_fkey" FOREIGN KEY (owner) REFERENCES users(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE "uploads"
DROP COLUMN "modified_by",
DROP CONSTRAINT "uploads_created_by_fkey",
DROP CONSTRAINT "uploads_owner_fkey",
ADD CONSTRAINT "uploads_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID,
ADD CONSTRAINT "uploads_owner_fkey" FOREIGN KEY (owner) REFERENCES users(id) ON DELETE CASCADE NOT VALID;

ALTER TABLE "user_group_roles"
DROP COLUMN "modified_by",
DROP CONSTRAINT "user_group_roles_created_by_fkey",
DROP CONSTRAINT "user_group_roles_user_id_fkey",
ADD CONSTRAINT "user_group_roles_created_by_fkey" FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE NOT VALID,
ADD CONSTRAINT "user_group_roles_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE NOT VALID;

COMMIT;

BEGIN;

ALTER TABLE "access_control_rules"
VALIDATE CONSTRAINT "access_control_rules_created_by_fkey";

ALTER TABLE "annotation_groups"
VALIDATE CONSTRAINT "annotation_groups_created_by_fkey";

ALTER TABLE "annotations"
VALIDATE CONSTRAINT "annotations_created_by_fkey",
VALIDATE CONSTRAINT "annotations_labeled_by_fkey",
VALIDATE CONSTRAINT "annotations_verified_by_fkey";

ALTER TABLE "aois"
VALIDATE CONSTRAINT "aois_created_by_fkey",
VALIDATE CONSTRAINT "aois_owner_fkey";

ALTER TABLE "datasources"
VALIDATE CONSTRAINT "datasources_created_by_fkey",
VALIDATE CONSTRAINT "datasources_owner_fkey";

ALTER TABLE "exports"
VALIDATE CONSTRAINT "exports_created_by_fkey",
VALIDATE CONSTRAINT "exports_owner_fkey";

ALTER TABLE "images"
VALIDATE CONSTRAINT "images_created_by_fkey",
VALIDATE CONSTRAINT "images_owner_fkey";

ALTER TABLE "map_tokens"
VALIDATE CONSTRAINT "map_tokens_created_by_fkey",
VALIDATE CONSTRAINT "map_tokens_owner_fkey";

ALTER TABLE "projects"
VALIDATE CONSTRAINT "projects_created_by_fkey",
VALIDATE CONSTRAINT "projects_owner_fkey";

ALTER TABLE "scenes"
VALIDATE CONSTRAINT "scenes_created_by_fkey",
VALIDATE CONSTRAINT "scenes_owner_fkey";

ALTER TABLE "shapes"
VALIDATE CONSTRAINT "shapes_created_by_fkey";

ALTER TABLE "task_actions"
VALIDATE CONSTRAINT "task_actions_user_id_fkey";

ALTER TABLE "tasks"
VALIDATE CONSTRAINT "tasks_created_by_fkey",
VALIDATE CONSTRAINT "tasks_locked_by_fkey",
VALIDATE CONSTRAINT "tasks_owner_fkey";

ALTER TABLE "teams"
VALIDATE CONSTRAINT "teams_created_by_fkey";

ALTER TABLE "tool_runs"
VALIDATE CONSTRAINT "tool_runs_created_by_fkey",
VALIDATE CONSTRAINT "tool_runs_owner_fkey";

ALTER TABLE "tools"
VALIDATE CONSTRAINT "tools_created_by_fkey",
VALIDATE CONSTRAINT "tools_owner_fkey";

ALTER TABLE "uploads"
VALIDATE CONSTRAINT "uploads_created_by_fkey",
VALIDATE CONSTRAINT "uploads_owner_fkey";

ALTER TABLE "user_group_roles"
VALIDATE CONSTRAINT "user_group_roles_created_by_fkey",
VALIDATE CONSTRAINT "user_group_roles_user_id_fkey";

COMMIT;
