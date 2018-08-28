import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M68 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(68)(
    List(
      sqlu"""
ALTER TABLE exports ALTER COLUMN project_id DROP NOT NULL;
"""
    ))
}
