import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M65 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(65)(
    List(
      sqlu"""
ALTER TABLE datasources DROP COLUMN color_correction;
"""
    ))
}
