import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M23 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(23)(
    List(
      sqlu"""
UPDATE buckets
  SET tags = '{}'
  WHERE tags IS NULL;

ALTER TABLE buckets
  ALTER COLUMN tags SET NOT NULL;
    """
    ))
}
