import com.liyaos.forklift.slick.{SqlMigration, DBIOMigration}
import java.sql.Timestamp
import java.util.{Calendar, Date, UUID}
import slick.jdbc.PostgresProfile.api._

/**
  * Data migration that adds a root user, who is a member of
  * the root organization.
  */
object M4 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(4)(
    List(
      sqlu"""
INSERT INTO users (id, created_at, modified_at, is_active, is_staff, email, first_name, last_name, organization_id)
VALUES('827d885a-b8f6-447f-8795-21c688af56e0', now(), now(), true, true, 'info+raster.foundry@azavea.com', 'Root', 'User', '9e2bef18-3f46-426b-a5bd-9913ee1ff840');
"""
    ))
}
