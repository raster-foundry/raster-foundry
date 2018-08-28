import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M98 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(98)(
    List(
      sqlu"""
    CREATE TABLE licenses (
      short_name VARCHAR(255) PRIMARY KEY NOT NULL,
      name VARCHAR(255) NOT NULL,
      url TEXT NOT NULL,
      osi_approved BOOLEAN NOT NULL
    );

    ALTER TABLE datasources
    ADD COLUMN license_name VARCHAR(255) REFERENCES licenses (short_name) NULL;
    """
    ))
}
