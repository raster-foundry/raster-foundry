import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M51 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(51)(
    List(
      sqlu"""
    CREATE TABLE feature_flags (
      id UUID PRIMARY KEY NOT NULL,
      key VARCHAR(255) NOT NULL UNIQUE,
      active BOOLEAN NOT NULL DEFAULT FALSE,
      name VARCHAR(255) NOT NULL,
      description VARCHAR(255) NOT NULL
    );
    """
    ))
}
