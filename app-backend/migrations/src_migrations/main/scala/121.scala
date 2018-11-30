import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M121 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(121)(
    List(
      sqlu"""
INSERT INTO user_group_roles (
id, created_at, created_by, modified_at, modified_by, user_id, group_type, group_id, group_role
) select uuid_generate_v4(), now(), 'default', now(), 'default', id, 'PLATFORM', '31277626-968b-4e40-840b-559d9c67863c', 'MEMBER' from users;
"""
    ))
}
