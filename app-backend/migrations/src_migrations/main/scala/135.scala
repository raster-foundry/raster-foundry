import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M135 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(135)(
    List(
      sqlu"""
ALTER TABLE licenses ADD COLUMN id SERIAL;
"""
    ))
}
