import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M79 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(79)(
    List(
      sqlu"""
ALTER TABLE projects
  ADD COLUMN is_single_band BOOLEAN default false,
  ADD COLUMN single_band_options JSONB;
"""
    ))
}
