import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M84 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(84)(
    List(
      sqlu"""
      ALTER TABLE users ADD COLUMN email_notifications BOOLEAN DEFAULT FALSE;
    """
    ))
}
