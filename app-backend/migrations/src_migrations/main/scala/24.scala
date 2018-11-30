import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M24 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(24)(
    List(
      sqlu"""
CREATE TABLE bands (
  id UUID PRIMARY KEY NOT NULL,
  image_id UUID REFERENCES images(id) NOT NULL,
  name VARCHAR(255) NOT NULL,
  number INTEGER NOT NULL,
  wavelength INTEGER[] NOT NULL
);

ALTER TABLE images DROP COLUMN bands;
    """
    ))
}
