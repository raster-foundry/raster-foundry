import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M18 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(18)(
    List(
      sqlu"""
ALTER TABLE scenes ADD COLUMN footprint geometry(Multipolygon, 3857);

UPDATE
    scenes
SET
    footprint = multipolygon
FROM
    footprints
WHERE
    scenes.id = footprints.scene_id;
"""
    ))
}
