import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M104 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(104)(
    List(
      sqlu"""
CREATE INDEX scenes_date_id_idx ON scenes (coalesce(acquisition_date, created_at), id);
"""
    ))
}
