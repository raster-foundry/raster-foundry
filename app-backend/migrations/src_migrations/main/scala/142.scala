import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M142 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(142)(
    List(
      sqlu"""
DELETE FROM scenes
WHERE cloud_cover < 0
AND datasource = '697a0b91-b7a8-446e-842c-97cda155554d';
"""
    ))
}
