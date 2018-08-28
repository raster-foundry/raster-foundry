import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M120 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(120)(
    List(
      sqlu"""
      ALTER TABLE organizations ADD COLUMN logo_uri TEXT NOT NULL DEFAULT '';
    """
    ))
}
