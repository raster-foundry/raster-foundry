import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M82 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(82)(
    List(
      sqlu"""
ALTER TABLE datasources ADD COLUMN bands JSONB;
"""
    ))
}
