import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M147 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(147)(
    List(
      sqlu"""
      ALTER TABLE scenes DROP COLUMN ingest_size_bytes;
    """
    ))
}
