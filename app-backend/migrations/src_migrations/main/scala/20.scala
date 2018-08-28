import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M20 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(20)(
    List(
      sqlu"""
ALTER TABLE scenes_to_buckets
  DROP CONSTRAINT scenes_to_buckets_bucket_id_fkey,
  ADD CONSTRAINT scenes_to_buckets_bucket_id_fkey
    FOREIGN KEY (bucket_id)
    REFERENCES buckets(id)
    ON DELETE CASCADE;

ALTER TABLE scenes_to_buckets
  DROP CONSTRAINT scenes_to_buckets_scene_id_fkey,
  ADD CONSTRAINT scenes_to_buckets_scene_id_fkey
    FOREIGN KEY (scene_id)
    REFERENCES scenes(id)
    ON DELETE CASCADE;
    """
    ))
}
