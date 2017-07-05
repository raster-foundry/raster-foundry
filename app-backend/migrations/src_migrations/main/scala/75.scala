import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M75 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(75)(List(
    sqlu"""
CREATE TABLE layer_attributes (
  layer_name VARCHAR(255) NOT NULL,
  zoom INTEGER NOT NULL,
  name VARCHAR(255) NOT NULL,
  value jsonb NOT NULL,
  PRIMARY KEY(layer_name, zoom, name)
);
"""
  ))
}
