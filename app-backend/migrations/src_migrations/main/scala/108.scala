import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M108 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(108)(List(
    sqlu"""
      ALTER TABLE platforms
        ADD COLUMN is_active BOOLEAN DEFAULT true NOT NULL;

      ALTER TABLE organizations
        ADD COLUMN is_active BOOLEAN DEFAULT true NOT NULL;

      ALTER TABLE teams
        ADD COLUMN is_active BOOLEAN DEFAULT true NOT NULL;

      ALTER TABLE users
        ADD COLUMN email VARCHAR(255) DEFAULT '' NOT NULL,
        ADD COLUMN name VARCHAR(255) DEFAULT '' NOT NULL,
        ADD COLUMN profile_image_uri TEXT  DEFAULT '' NOT NULL,
        ADD COLUMN is_superuser BOOLEAN DEFAULT false NOT NULL,
        ADD COLUMN is_active BOOLEAN DEFAULT true NOT NULL;
    """
  ))
}
