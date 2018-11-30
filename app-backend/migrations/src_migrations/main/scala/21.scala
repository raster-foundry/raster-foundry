import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M21 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(21)(
    List(
      sqlu"""
ALTER TABLE images
  ADD COLUMN resolution_meters REAL;

UPDATE images i
  SET resolution_meters = s.resolution_meters
  FROM scenes s
  WHERE i.scene = s.id;

ALTER TABLE images
  ALTER COLUMN resolution_meters SET NOT NULL;

ALTER TABLE scenes
  DROP COLUMN resolution_meters;
    """
    ))
}
