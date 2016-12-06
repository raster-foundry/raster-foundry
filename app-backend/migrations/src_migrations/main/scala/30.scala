import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M30 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(30)(List(
    sqlu"""
ALTER TABLE SCENES RENAME footprint TO tile_footprint;
ALTER TABLE SCENES ADD COLUMN data_footprint geometry(Multipolygon, 3857);
"""
  ))
}
