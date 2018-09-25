import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M134 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(134)(
    List(
      sqlu"""
UPDATE user_group_roles SET is_active = false WHERE id NOT IN (
SELECT ugr.id FROM user_group_roles ugr JOIN (
  SELECT group_id, user_id, MAX(created_at) AS created_at
  FROM user_group_roles GROUP BY group_id, user_id
) latest_time ON
latest_time.group_id = ugr.group_id AND
latest_time.user_id = ugr.user_id AND
latest_time.created_at = ugr.created_at
);

CREATE UNIQUE INDEX user_group_role_unique_role ON user_group_roles (group_id, user_id) WHERE (is_active = true);
"""
    ))
}
