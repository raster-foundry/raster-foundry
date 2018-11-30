import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M36 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(36)(
    List(
      sqlu"""
ALTER TABLE scenes DROP COLUMN status;
ALTER TABLE scenes ADD COLUMN ingest_location text;
"""
    ))
}
