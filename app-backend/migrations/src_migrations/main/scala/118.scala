import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M118 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(118)(
    List(
      sqlu"""
----
-- everyone can view and download public scenes, but can't do anything else to them
----

INSERT INTO access_control_rules (
  id, created_at, created_by, is_active, object_type, object_id, subject_type, subject_id, action_type
) (
  SELECT
    uuid_generate_v4(), now(), 'default', true, 'SCENE', id, 'ALL', null, 'VIEW' from scenes
  WHERE
   visibility = 'PUBLIC'
);

INSERT INTO access_control_rules (
  id, created_at, created_by, is_active, object_type, object_id, subject_type, subject_id, action_type
) (
  SELECT
    uuid_generate_v4(), now(), 'default', true, 'SCENE', id, 'ALL', null, 'DOWNLOAD' from scenes
  WHERE
   visibility = 'PUBLIC'
);

----
-- everyone can view, export, and annotate public projects
----

INSERT INTO access_control_rules (
  id, created_at, created_by, is_active, object_type, object_id, subject_type, subject_id, action_type
) (
  SELECT
    uuid_generate_v4(), now(), 'default', true, 'PROJECT', id, 'ALL', null, 'VIEW' from projects
  WHERE
   visibility = 'PUBLIC'
);

INSERT INTO access_control_rules (
  id, created_at, created_by, is_active, object_type, object_id, subject_type, subject_id, action_type
) (
  SELECT
    uuid_generate_v4(), now(), 'default', true, 'PROJECT', id, 'ALL', null, 'EXPORT' from projects
  WHERE
   visibility = 'PUBLIC'
);

INSERT INTO access_control_rules (
  id, created_at, created_by, is_active, object_type, object_id, subject_type, subject_id, action_type
) (
  SELECT
    uuid_generate_v4(), now(), 'default', true, 'PROJECT', id, 'ALL', null, 'ANNOTATE' from projects
  WHERE
   visibility = 'PUBLIC'
);

----
-- everyone can view public datasources
----

INSERT INTO access_control_rules (
  id, created_at, created_by, is_active, object_type, object_id, subject_type, subject_id, action_type
) (
  SELECT
    uuid_generate_v4(), now(), 'default', true, 'DATASOURCE', id, 'ALL', null, 'VIEW' from datasources
  WHERE
   visibility = 'PUBLIC'
);

----
-- everyone can view public templates
----

INSERT INTO access_control_rules (
  id, created_at, created_by, is_active, object_type, object_id, subject_type, subject_id, action_type
) (
  SELECT
    uuid_generate_v4(), now(), 'default', true, 'TEMPLATE', id, 'ALL', null, 'VIEW' from tools
  WHERE
   visibility = 'PUBLIC'
);

----
-- everyone can view and export public analyses
----

INSERT INTO access_control_rules (
  id, created_at, created_by, is_active, object_type, object_id, subject_type, subject_id, action_type
) (
  SELECT
    uuid_generate_v4(), now(), 'default', true, 'ANALYSIS', id, 'ALL', null, 'VIEW' from tool_runs
  WHERE
   visibility = 'PUBLIC'
);

INSERT INTO access_control_rules (
  id, created_at, created_by, is_active, object_type, object_id, subject_type, subject_id, action_type
) (
  SELECT
    uuid_generate_v4(), now(), 'default', true, 'ANALYSIS', id, 'ALL', null, 'EXPORT' from tool_runs
  WHERE
   visibility = 'PUBLIC'
);

"""
    ))
}
