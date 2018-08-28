import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M133 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(133)(
    List(
      sqlu"""
UPDATE platforms
SET public_settings = public_settings || '{"platformHost": null}';
"""
    ))
}
