import com.liyaos.forklift.slick.{SqlMigration, DBIOMigration}
import java.sql.Timestamp
import java.util.{Calendar, Date, UUID}
import slick.jdbc.PostgresProfile.api._

/**
  * Data migration that adds a root organization
  */
object M2 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(2)(
    List(
      sqlu"""
INSERT INTO organizations (id, created_at, modified_at, name)
VALUES('9e2bef18-3f46-426b-a5bd-9913ee1ff840', now(), now(), 'root organization');
"""
    ))
}
