import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M153 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(153)(
    List(
      sqlu"""
UPDATE projects
SET aoi_cadence_millis = 86400 * 1000
WHERE aoi_cadence_millis > 86400 * 1000;
"""
    )
  )
}
