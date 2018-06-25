import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M134 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(134)(List(
    sqlu"""
CREATE UNIQUE INDEX user_group_role_unique_role ON user_group_roles (group_id, user_id) WHERE (is_active = true);
"""
  ))
}
