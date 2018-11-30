import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M78 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(78)(
    List(
      sqlu"""
ALTER TABLE images
  DROP CONSTRAINT images_scene_fkey,
  ADD CONSTRAINT images_scene_fkey
    FOREIGN KEY (scene)
    REFERENCES scenes(id)
    ON DELETE CASCADE;
ALTER TABLE thumbnails
  DROP CONSTRAINT thumbnails_scene_fkey,
  ADD CONSTRAINT thumbnails_scene_fkey
    FOREIGN KEY (scene)
    REFERENCES scenes(id)
    ON DELETE CASCADE;
ALTER TABLE bands
  DROP CONSTRAINT bands_image_id_fkey,
  ADD CONSTRAINT bands_image_id_fkey
    FOREIGN KEY (image_id)
    REFERENCES images(id)
    ON DELETE CASCADE;
"""
    ))
}
