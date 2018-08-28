import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M114 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(114)(
    List(
      sqlu"""

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

INSERT INTO user_group_roles (
  id, created_at, created_by, modified_at, modified_by, is_active,
  user_id, group_type, group_id, group_role
) (
  SELECT
    uuid_generate_v4(), NOW(), 'default', NOW(), 'default', true, id,
    'ORGANIZATION' :: group_type, organization_id, 'ADMIN' :: group_role
  FROM
    users
  WHERE
    role = 'ADMIN'
);

INSERT INTO user_group_roles (
  id, created_at, created_by, modified_at, modified_by, is_active,
  user_id, group_type, group_id, group_role
) (
  SELECT
    uuid_generate_v4(), NOW(), 'default', NOW(), 'default', true, id,
    'ORGANIZATION' :: group_type, organization_id, 'MEMBER' :: group_role
  FROM
    users
  WHERE
    role = 'VIEWER'
);

ALTER TABLE annotations DROP COLUMN organization_id;
ALTER TABLE aois DROP COLUMN organization_id;
ALTER TABLE datasources DROP COLUMN organization_id;
ALTER TABLE exports DROP COLUMN organization_id;
ALTER TABLE images DROP COLUMN organization_id;
ALTER TABLE map_tokens DROP COLUMN organization_id;
ALTER TABLE projects DROP COLUMN organization_id;
ALTER TABLE scenes DROP COLUMN organization_id;
ALTER TABLE shapes DROP COLUMN organization_id;
ALTER TABLE thumbnails DROP COLUMN organization_id;
ALTER TABLE tool_runs DROP COLUMN organization_id;
ALTER TABLE tool_tags DROP COLUMN organization_id;
ALTER TABLE tools DROP COLUMN organization_id;
ALTER TABLE uploads DROP COLUMN organization_id;
ALTER TABLE users DROP COLUMN organization_id;
"""
    ))
}
