import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M60 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(60)(
    List(
      sqlu"""
ALTER TABLE tool_runs DROP COLUMN project
"""
    ))
}
