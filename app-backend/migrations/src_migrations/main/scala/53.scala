import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M53 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(53)(
    List(
      sqlu"""
    ALTER TABLE uploads
      ADD COLUMN visibility visibility DEFAULT 'PRIVATE' NOT NULL;
    """
    ))
}
