import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M66 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(66)(
    List(
      sqlu"""
ALTER TABLE exports ADD COLUMN toolrun_id UUID REFERENCES tool_runs(id);
"""
    ))
}
