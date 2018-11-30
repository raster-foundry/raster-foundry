import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M32 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(32)(
    List(
      sqlu"""
ALTER TABLE scenes DROP COLUMN data_footprint;
ALTER TABLE scenes RENAME COLUMN tile_footprint TO data_footprint;
ALTER TABLE scenes ADD COLUMN tile_footprint geometry(Multipolygon, 3857);
""" // your sql code goes here
    ))
}
