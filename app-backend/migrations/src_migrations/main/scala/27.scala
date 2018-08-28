import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M27 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(27)(
    List(
      sqlu"""
ALTER TABLE buckets RENAME TO projects;

ALTER TABLE scenes_to_buckets RENAME TO scenes_to_projects;

ALTER TABLE scenes_to_projects RENAME COLUMN bucket_id TO project_id;

ALTER TABLE projects RENAME CONSTRAINT
  buckets_pkey TO projects_pkey;
ALTER TABLE projects RENAME CONSTRAINT
  buckets_created_by_fkey TO projects_created_by_fkey;
ALTER TABLE projects RENAME CONSTRAINT
  buckets_modified_by_fkey TO projects_modified_by_fkey;
ALTER TABLE projects RENAME CONSTRAINT
  buckets_organization_id_fkey TO projects_organization_id_fkey;

ALTER TABLE scenes_to_projects RENAME CONSTRAINT
  scenes_to_buckets_pkey TO scenes_to_projects_pkey;
ALTER TABLE scenes_to_projects RENAME CONSTRAINT
  scenes_to_buckets_bucket_id_fkey TO scenes_to_projects_project_id_fkey;
ALTER TABLE scenes_to_projects RENAME CONSTRAINT
  scenes_to_buckets_scene_id_fkey TO scenes_to_projects_scene_id_fkey;

"""
    ))
}
