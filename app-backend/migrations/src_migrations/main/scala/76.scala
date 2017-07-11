import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M76 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(76)(List(
    sqlu"""
CREATE TABLE IF NOT EXISTS layer_attributes (
  layer_name VARCHAR(255) NOT NULL,
  zoom INTEGER NOT NULL,
  name VARCHAR(255) NOT NULL,
  value jsonb NOT NULL,
  PRIMARY KEY(layer_name, zoom, name)
);
"""
  ))
}
