import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M80 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(80)(List(
    sqlu"""
ALTER TABLE uploads ALTER COLUMN upload_type TYPE varchar(255);
DROP TYPE upload_type;
CREATE TYPE upload_type AS ENUM ('DROPBOX', 'S3', 'LOCAL', 'PLANET');
ALTER TABLE uploads ALTER COLUMN upload_type TYPE upload_type using upload_type::upload_type;
"""
  ))
}
